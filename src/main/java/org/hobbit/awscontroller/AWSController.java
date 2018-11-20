package org.hobbit.awscontroller;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.kms.model.NotFoundException;
import com.amazonaws.services.neptune.AmazonNeptune;
import com.amazonaws.services.neptune.AmazonNeptuneClient;
import com.amazonaws.services.neptune.AmazonNeptuneClientBuilder;
import com.amazonaws.services.neptune.model.AddRoleToDBClusterRequest;
import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.profile.internal.AllProfiles;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.auth.profile.internal.ProfileAssumeRoleCredentialsProvider;
import com.amazonaws.auth.profile.internal.securitytoken.ProfileCredentialsService;
import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import com.amazonaws.services.cloudformation.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;


public class AWSController {

    private final Logger logger = LoggerFactory.getLogger(AWSController.class);
    public AmazonCloudFormation amazonCloudFormation;
    public AmazonAutoScaling amazonAutoScaling;
    public AmazonNeptune amazonNeptune;
    private AmazonS3 amazonS3;
    private AmazonEC2 amazonEC2;
    private Semaphore operationFinishedMutex = new Semaphore(0);
    Map<String, String> stackIds = new HashMap<>();
    List<Exception> exceptions = new ArrayList<>();

    String aws_access_key_id;
    String aws_secret_key;
    String aws_role_arn;
    String aws_region;

    public static ExecutorService es;
    Semaphore finishedMutex = new Semaphore(0);

    public AWSController(){

    }

    public AWSController(String aws_access_key_id, String aws_secret_key, String aws_role_arn, String aws_region){
        this.aws_access_key_id = aws_access_key_id;
        this.aws_secret_key = aws_secret_key;
        this.aws_role_arn = aws_role_arn;
        this.aws_region = aws_region;
    }

    public void setAws_access_key_id(String aws_access_key_id) {
        this.aws_access_key_id = aws_access_key_id;
    }

    public void setAws_secret_key(String aws_secret_key) {
        this.aws_secret_key = aws_secret_key;
    }

    public void setAws_role_arn(String aws_role_arn) {
        this.aws_role_arn = aws_role_arn;
    }

    public void setAws_region(String aws_region) {
        this.aws_region = aws_region;
    }

    public void init() throws Exception {

        es = Executors.newFixedThreadPool(4);

        if(aws_access_key_id==null)
            if(!System.getenv().containsKey("AWS_ACCESS_KEY_ID"))
                throw new Exception("AWS_ACCESS_KEY_ID is missing");
            else aws_access_key_id = System.getenv("AWS_ACCESS_KEY_ID");

        if(aws_secret_key==null)
            if(!System.getenv().containsKey("AWS_SECRET_KEY"))
                throw new Exception("AWS_SECRET_KEY is missing");
            else aws_secret_key = System.getenv("AWS_SECRET_KEY");

        if(aws_role_arn==null)
            if(!System.getenv().containsKey("AWS_ROLE_ARN"))
                throw new Exception("AWS_ROLE_ARN is missing");
            else aws_role_arn = System.getenv("AWS_ROLE_ARN");

        if(aws_region==null)
            if( !System.getenv().containsKey("AWS_REGION"))
                throw new Exception("AWS_REGION is missing");
            else aws_region = System.getenv("AWS_REGION");

        Map<String, String> defaultProfileProperties = new HashMap<>();
        defaultProfileProperties.put("region", aws_region);
        defaultProfileProperties.put("aws_access_key_id", aws_access_key_id);
        defaultProfileProperties.put("aws_secret_access_key", aws_secret_key);

        BasicProfile defaultProfile = new BasicProfile("default", defaultProfileProperties);

        Map<String, String> properties = new HashMap<>();
        properties.put("role_arn", aws_role_arn);
        properties.put("source_profile", "default");

        BasicProfile roleProfile = new BasicProfile("gs", properties);

        Map<String, BasicProfile> allProfiles = new HashMap<>();
        allProfiles.put(defaultProfile.getProfileName(), defaultProfile);
        allProfiles.put(roleProfile.getProfileName(), roleProfile);

        AWSCredentialsProvider credentialsProvider = new ProfileAssumeRoleCredentialsProvider(
                new ProfileCredentialsService(){
                    @Override
                    public AWSCredentialsProvider getAssumeRoleCredentialsProvider(RoleInfo roleInfo) {
                        return new STSProfileCredentialsServiceProvider(roleInfo);
                    }
                }, new AllProfiles(allProfiles), roleProfile
        );

        AmazonCloudFormationClientBuilder amazonCloudFormationClientBuilder = AmazonCloudFormationClientBuilder.standard();
        amazonCloudFormationClientBuilder.setRegion(aws_region);
        amazonCloudFormationClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonCloudFormationClientBuilder.setCredentials(credentialsProvider);
        amazonCloudFormation = amazonCloudFormationClientBuilder.build();

        AmazonAutoScalingClientBuilder amazonAutoScalingClientBuilder = AmazonAutoScalingClientBuilder.standard();
        amazonAutoScalingClientBuilder.setRegion(aws_region);
        amazonAutoScalingClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonAutoScalingClientBuilder.setCredentials(credentialsProvider);
        amazonAutoScaling = amazonAutoScalingClientBuilder.build();

        AmazonEC2AsyncClientBuilder amazonEC2AsyncClientBuilder = AmazonEC2AsyncClientBuilder.standard();
        amazonEC2AsyncClientBuilder.setRegion(aws_region);
        amazonEC2AsyncClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonEC2AsyncClientBuilder.setCredentials(credentialsProvider);
        amazonEC2 = amazonEC2AsyncClientBuilder.build();

        AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard();
        amazonS3ClientBuilder.setRegion(aws_region);
        amazonS3ClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonS3ClientBuilder.setCredentials(credentialsProvider);
        amazonS3 = amazonS3ClientBuilder.build();

        AmazonNeptuneClientBuilder amazonNeptuneClientBuilder = AmazonNeptuneClientBuilder.standard();
        amazonNeptuneClientBuilder.setRegion(aws_region);
        amazonNeptuneClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonNeptuneClientBuilder.setCredentials(credentialsProvider);
        amazonNeptune = amazonNeptuneClientBuilder.build();

//        AmazonAutoScalingClientBuilder amazonAutoScalingBuilder = AmazonAutoScalingClientBuilder.standard();
//        amazonAutoScalingBuilder.setRegion(AWS_REGION_EU);
//        amazonAutoScalingBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
//        amazonAutoScalingBuilder.setCredentials(credentialsProvider);
//        amazonAutoScaling = amazonAutoScalingBuilder.build();

    }

    public String getRegion() {
        return aws_region;
    }

    public AmazonAutoScaling getAmazonAutoScaling() {
        return amazonAutoScaling;
    }

    public AmazonCloudFormation getAmazonCloudFormation() {
        return amazonCloudFormation;
    }

    public AmazonEC2 getAmazonEC2() {
        return amazonEC2;
    }

    public AmazonS3 getAmazonS3() {
        return amazonS3;
    }

    public AmazonNeptune getAmazonNeptune() {
        return amazonNeptune;
    }

    public StackSummary findStackByName(String name) throws Exception{
        List<StackSummary> ret = new ArrayList<>();
        ListStacksResult result;
        String nextToken = "";

        while(ret.size()==0 && nextToken!=null){

            if(nextToken.equals(""))
                result = amazonCloudFormation.listStacks();
            else{
                ListStacksRequest listStacksRequest = new ListStacksRequest();
                listStacksRequest.setNextToken(nextToken);
                result = amazonCloudFormation.listStacks(listStacksRequest);
            }
            nextToken = result.getNextToken();
            ret = result.getStackSummaries().stream().filter(
                    s->s.getStackName().equals(name) && !s.getStackStatus().equals("DELETE_COMPLETE")
            ).collect(Collectors.toList());
        }

        if(ret.size()==0)
            return null;

        return ret.get(0);
    }

    public void addRoleToDBCluster(String clusterId, String roleArn) throws Exception{
        AddRoleToDBClusterRequest request = new AddRoleToDBClusterRequest();
        request.setDBClusterIdentifier(clusterId);
        request.setRoleArn(roleArn);
        amazonNeptune.addRoleToDBCluster(request);
    }

    public StackSummary findStackById(String id) throws Exception{
        List<StackSummary> ret = new ArrayList<>();
        ListStacksResult result;
        String nextToken = "";

        while(ret.size()==0 && nextToken!=null){

            if(nextToken.equals(""))
                result = amazonCloudFormation.listStacks();
            else{
                ListStacksRequest listStacksRequest = new ListStacksRequest();
                listStacksRequest.setNextToken(nextToken);
                result = amazonCloudFormation.listStacks(listStacksRequest);
            }
            nextToken = result.getNextToken();
            ret = result.getStackSummaries().stream().filter(
                    s->s.getStackId().equals(id) && !s.getStackStatus().equals("DELETE_COMPLETE")
            ).collect(Collectors.toList());
        }

        if(ret.size()==0)
            return null;

        return ret.get(0);
    }

    public AmazonCloudFormation getCloudFormation(){
        return amazonCloudFormation;
   }

    public List<StackResourceSummary> getStackResources(String stackName) throws Exception{
        return getStackResources(stackName, null);
    }

    public Map<String, String> getStackResourcesMap(String stackName) throws Exception{
        Map<String, String> ret = new TreeMap<>();
        List<StackResourceSummary> summaries = getStackResources(stackName, null);
        for(StackResourceSummary summary : summaries)
            ret.put(summary.getLogicalResourceId(), summary.getPhysicalResourceId());
        return ret;
    }

    public List<StackResourceSummary> getStackResources(String stackName, String awsResourceType) throws Exception{
       List<StackResourceSummary> ret = new ArrayList<>();
       StackSummary summary = findStackByName(stackName);
       if(summary==null)
           throw new NotFoundException("Stack "+stackName+" not exists");
       ListStackResourcesRequest listStackResourcesRequest = new ListStackResourcesRequest();
       listStackResourcesRequest.setStackName(stackName);
       try {
           ListStackResourcesResult result = amazonCloudFormation.listStackResources(listStackResourcesRequest);
           ret = result.getStackResourceSummaries();
           if (awsResourceType != null)
               ret = ret.stream().filter(item -> item.getResourceType().equals(awsResourceType)).collect(Collectors.toList());
           return ret;
       }
       catch (Exception e){
           logger.error(e.getMessage());
           throw new Exception(e.getMessage());
       }

   }

    public List<Output> getStackOutputs(String stackName) throws Exception{
        List<Output> ret = new ArrayList<>();
        StackSummary summary = findStackByName(stackName);
        if(summary==null)
            throw new NotFoundException("Stack "+stackName+" not exists");
        DescribeStacksRequest request = new DescribeStacksRequest();
        request.setStackName(stackName);
        try {
            DescribeStacksResult  result = amazonCloudFormation.describeStacks(request);
            ret = result.getStacks().get(0).getOutputs();
        }
        catch (Exception e){
            logger.error(e.getMessage());
            throw new Exception("Cannot get stack outputs: "+ e.getLocalizedMessage());
        }
        return ret;
    }

    public Map<String, String>  getStackOutputsMap(String stackName) throws Exception{
        Map<String, String> ret = new TreeMap<>();
        List<Output> res = getStackOutputs(stackName);
        for(Output output: res)
            ret.put(output.getOutputKey(), output.getOutputValue());
        return ret;
    }

    public List<AutoScalingGroup> getAutoscalingGroupByName(String name) throws Exception{
        DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
        request.setAutoScalingGroupNames(Arrays.asList(new String[]{ name }));
        DescribeAutoScalingGroupsResult result = amazonAutoScaling.describeAutoScalingGroups(request);
        List<AutoScalingGroup>  list = result.getAutoScalingGroups();
        return list;
    }

    public List<Instance> getEC2InstanceByName(String name) throws Exception{
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Arrays.asList(new String[]{ name }));
        DescribeInstancesResult result = amazonEC2.describeInstances(request);
        List<Instance> ret = result.getReservations().get(0).getInstances();
        return ret;
    }

    public List<Instance> getEC2InstancesByAutoscalingGroupName(String name) throws Exception{
        List<Instance> ret = new ArrayList<>();
        List<AutoScalingGroup>  asgList = getAutoscalingGroupByName(name);
        if(asgList.isEmpty())
            return ret;

        List<com.amazonaws.services.autoscaling.model.Instance> stackInstancesList = asgList.get(0).getInstances();
        if(stackInstancesList.isEmpty())
            return ret;

        for(com.amazonaws.services.autoscaling.model.Instance instance : stackInstancesList){
            String instanceId = instance.getInstanceId();
            List<com.amazonaws.services.ec2.model.Instance> ecInstancesList = getEC2InstanceByName(instanceId);
            ret.addAll(ecInstancesList);
        }
        return ret;
    }


    public void waitForCompletion(AbstractStackHandler stack, String until) throws Exception {
        waitForCompletion(stack, until, null);
    }

    public void waitForCompletion(AbstractStackHandler stack, String until, String exception) throws Exception {
        //operationFinishedMutex.release();
        logger.info("Waiting for {} for stack {}", until, stack.getName());
        List<String> untilList = Arrays.asList(until);
        long started = new Date().getTime();
        boolean stop = false;
        String error=null;
        while (!stop) {


        StackSummary stackSummary = (stack.getId() != null ? findStackById(stack.getId()) : findStackByName(stack.getName()));
        if(stackSummary==null)
            stop = true;
        else
            if (stackSummary.getStackStatus().startsWith(until)) {
                stop = true;
            } else if (exception != null && stackSummary.getStackStatus().startsWith(exception)) {
                String errorMessage = String.format("Stack %s not reached the state %s: %s", stack.getName(), until, stackSummary.getStackStatusReason());
                throw new Exception(errorMessage);
            } else {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                }
            }

        }

        logger.info("Stack {} reached the state {} after {} s", stack.getName(), until, String.valueOf((new Date().getTime()-started)/1000));
        //operationFinishedMutex.release();
    }

//    public static void createPubliclyAvailableBucket(String bucketName){
//
//        if(amazonS3.listBuckets().stream().filter(bucket -> bucket.getName().equals(bucketName)).count()>0) {
//            logger.info("Bucket {} already created", bucketName);
//
//        }else {
//            CreateBucketRequest request = new CreateBucketRequest(bucketName, AWS_REGION_EU);
//
//            AccessControlList acl = new AccessControlList();
//            acl.grantPermission(GroupGrantee.AllUsers, Permission.Write);
//
//            request.setAccessControlList(acl);
//            Bucket result = amazonS3.createBucket(request);
//            logger.info("Bucket {} created", bucketName);
//        }
//    }
    public void createBucket(String bucketName){
        createBucket(bucketName, null);
    }

    public void createBucket(String bucketName, String region){
        if(region==null)
            region = aws_region;
        if(amazonS3.listBuckets().stream().filter(bucket -> bucket.getName().equals(bucketName)).count()>0) {
            logger.info("Bucket {} already created", bucketName);

        }else {
            CreateBucketRequest request = new CreateBucketRequest(bucketName, region);

            //AccessControlList acl = new AccessControlList();
            //acl.grantPermission(GroupGrantee.AllUsers, Permission.Write);

            //request.setAccessControlList(acl);
            Bucket result = amazonS3.createBucket(request);
            logger.info("Bucket {} created", bucketName);
        }
    }

    public void putObjectToS3(String bucketName, File file) throws Exception{
        PutObjectRequest request = new PutObjectRequest(bucketName, file.getName() ,file);
        PutObjectResult result = amazonS3.putObject(request);
    }

    public void switchBucketAvailability(String bucketName, Boolean publicWrite){
        logger.info("Switching bucket availability (publicWrite={}) for bucket {} ", publicWrite, bucketName);
        Collection<Grant> grantCollection = new ArrayList<Grant>();

        Grant grant1 = new Grant(new CanonicalGrantee(amazonS3.getS3AccountOwner().getId()), Permission.FullControl);
        grantCollection.add(grant1);

        if(publicWrite) {
            Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Write);
            grantCollection.add(grant2);
        }

        AccessControlList acl = amazonS3.getBucketAcl(bucketName);
        acl.getGrantsAsList().clear();
        acl.getGrantsAsList().addAll(grantCollection);
        amazonS3.setBucketAcl(bucketName, acl);
    }

    public void setPublicAvailabilityToS3File(String bucketName, String fileKey){

        logger.info("Setting Public Availability To S3 File {} (bucket={})", fileKey, bucketName);
        Collection<Grant> grantCollection = new ArrayList<Grant>();

        Grant grant1 = new Grant(new CanonicalGrantee(amazonS3.getS3AccountOwner().getId()), Permission.FullControl);
        grantCollection.add(grant1);

        Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Read);
        grantCollection.add(grant2);

        AccessControlList objectAcl = amazonS3.getObjectAcl(bucketName, fileKey);
        objectAcl.getGrantsAsList().clear();
        objectAcl.getGrantsAsList().addAll(grantCollection);
        amazonS3.setObjectAcl(bucketName, fileKey, objectAcl);
    }

//    public void createStacksSequental(List<AbstractStackHandler> stackList) throws Exception {
//        for (AbstractStackHandler stack : stackList){
//            createStack(stack);
//        }
//    }

    public List<String> createStacks(List<List<AbstractStackHandler>> hierarchicalStackList) throws Exception {
        return createStacks(hierarchicalStackList, false);
    }

    public List<String> createStacks(List<List<AbstractStackHandler>> hierarchicalStackList, boolean deleteStacksIfConfigNotMatching) throws Exception {

        if(deleteStacksIfConfigNotMatching){
            List<List<AbstractStackHandler>> stackToDelete = findStacksToDelete(hierarchicalStackList);
            deleteStacks(stackToDelete);
        }
        List<String> newlyCreatedStackIds = new ArrayList<>();
        processStacksByLevels(hierarchicalStackList, new Callback<AbstractStackHandler, String>(){
            @Override
            public String call(AbstractStackHandler stack){
                String stackId = null;
                //while (stackId == null){
                //while(stackId==null) {
                    try {
                        stackId = createStack(stack);
                        if(stackId!=null)
                            newlyCreatedStackIds.add(stackId);
                    } catch (Exception e) {
                        logger.error("Stack {} was not finished:{}", stack.getName(), e.getLocalizedMessage());
                        //logger.info("Restarting {}",stack.getName());
                        exceptions.add(e);
                    }
                //}
                return null;
            }
        });
        return newlyCreatedStackIds;
    }

    public List<List<AbstractStackHandler>> findStacksToDelete(List<List<AbstractStackHandler>> hierarchicalStackList){
        logger.info("Checking already existing stacks");
        List<String> names = new ArrayList<>();
        List<List<AbstractStackHandler>> ret = new ArrayList<>();
        for (List<AbstractStackHandler> level: hierarchicalStackList){
            List<AbstractStackHandler> levelToDelete = new ArrayList<>();
            for (AbstractStackHandler stack : level){
                try {
                    StackSummary summary = findStackByName(stack.getName());
                    if (summary.getStackStatus().equals("CREATE_COMPLETE")){
                        List<Parameter> parameters = getStackParameters(stack.getName());
                        boolean requiresRecreation = false;
                        if (parameters != null) {
                            Map<String, String> existingStackParams = new HashMap<>();
                            for (Parameter parameter : parameters)
                                existingStackParams.put(parameter.getParameterKey(), parameter.getParameterValue());

                            for (String paramName : stack.getParameters().keySet()) {
                                String exisingParamValue = existingStackParams.get(paramName);
                                String desiredParamValue = stack.getParameters().get(paramName);
                                if (!existingStackParams.containsKey(paramName) || (!exisingParamValue.equals("****") && !existingStackParams.get(paramName).equals(desiredParamValue))) {
                                    requiresRecreation = true;
                                    logger.info("Param {}={}, not {} for the {} stack", paramName, exisingParamValue, desiredParamValue, stack.getName());
                                }
                            }

                        } else
                            requiresRecreation = true;

                        if (requiresRecreation) {
                            logger.info("Stack {} requires recreation with new parameters. The existing will be deleted", stack.getName());
                            levelToDelete.add(stack);
                            names.add(stack.getName());
                        }
                    }
                }
                catch (NotFoundException e){
                    //logger.error("");
                }
                catch (Exception e){
                    //logger.error("Problem with getting stack parameters: {}", stack.getName());
                }

            }
            if(levelToDelete.size()>0)
                ret.add(levelToDelete);
        }
        if(names.size()>0)
            logger.info("The following stacks will be deleted: {}", String.join(",", names));
        return ret;
    }

    public void deleteStacksSequental(List<AbstractStackHandler> stackList) throws Exception {
        for (AbstractStackHandler stack : stackList){
            deleteStack(stack);
        }
    }

    public void deleteStacks(List<List<AbstractStackHandler>> hierarchicalStackList) throws Exception {

        processStacksByLevels(Lists.reverse(hierarchicalStackList), new Callback<AbstractStackHandler, String>(){
            @Override
            public String call(AbstractStackHandler stack){
                String stackId = null;
                //while (stackId == null){
                try {
                    deleteStack(stack);
                    logger.debug("Stack {} was deleted", stack.getName());
                } catch (Exception e) {
                    logger.error("Stack {} was not deleted: {}", stack.getName(), e.getMessage());
                    //logger.info("Trying to recreate the stack {}", stack.getName());
                }
                return null;
            }
        });

//        List<Callable<String>> tasks = new ArrayList();
//        for (List<AbstractStack> stackList: hierarchicalStackList)
//            if(stackList.size()>0){
//                Callable<String> task = new Callable<String>(){
//                    @Override
//                    public String call() throws Exception {
//                        List<AbstractStack> unprocessedStacks = new ArrayList<>(stackList);
//                        while (!unprocessedStacks.isEmpty())
//                            try {
//                                AbstractStack stack = unprocessedStacks.get(unprocessedStacks.size() - 1);
//                                deleteStack(stack);
//                                unprocessedStacks.remove(stack);
//                            } catch (Exception e) {
//                                logger.error(e.getMessage());
//                            }
//                        return null;
//                    }
//                };
//                tasks.add(task);
//            }
//
//
//        es.invokeAll(tasks);
    }

    private void processStacksByLevels(List<List<AbstractStackHandler>> hierarchicalStackList, Callback<AbstractStackHandler, String> action) throws Exception {


        for (List<AbstractStackHandler> level: hierarchicalStackList){
            List<Callable<String>> levelTasks = new ArrayList();
            List<String> layerStackNames = new ArrayList<>();
            int i=0;
            for (AbstractStackHandler stack : level){
                layerStackNames.add(stack.getName());
                final int sleep = i*1000;
                Callable<String> task = new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        Thread.sleep(sleep);   //sometimes rate exceeded error can occur
                        action.call(stack);
                        finishedMutex.release();
                        return null;
                    }
                };
                levelTasks.add(task);
                i+=2;
            }
            long started = new Date().getTime();
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            try {
                                es.invokeAll(levelTasks);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    },
                    100
            );

            finishedMutex.acquire(levelTasks.size());
            long took = (new Date().getTime()-started)/1000;

            if (exceptions.size()>0) {
                for(Exception exception : exceptions)
                    exception.printStackTrace();
                throw new Exception("Stack level was not finished");
            }

            if(took>0)
                logger.info("Layer {} finished in {} s", String.join("_", layerStackNames), took);
        }
    }

    public List<Parameter> getStackParameters(String stackName) throws Exception{
        DescribeStacksRequest req = new DescribeStacksRequest();
        req.setStackName(stackName);
        DescribeStacksResult res = amazonCloudFormation.describeStacks(req);
        if(res.getStacks().size()>0)
            return res.getStacks().get(0).getParameters();
        return null;
    }

    public String createStack(AbstractStackHandler stack) throws Exception {

        StackSummary stackSummary = findStackByName(stack.getName());
        if(stackSummary!=null){
          if (stackSummary.getStackStatus().startsWith("CREATE_")){
                if(stackSummary.getStackStatus().equals("CREATE_IN_PROGRESS"))
                    waitForCompletion(stack, "CREATE_COMPLETE");
                stack.setId(stackSummary.getStackId());
                logger.info("Stack {} created", stack.getName());
            } else if (stackSummary.getStackStatus().startsWith("ROLLBACK_")) {
                logger.info("A rollbacked stack found. Deleting: {}", stack.getName());
                deleteStack(stack);
                //AWSController.waitForCompletion(stack, "DELETE_COMPLETE");
            } else if (stackSummary.getStackStatus().equals("DELETE_IN_PROGRESS")) {
                logger.info("A stack {} with DELETE_IN_PROGRESS found", stack.getName());
                waitForCompletion(stack, "DELETE_COMPLETE");
            }
        }

        if(stack.getId()!=null)
            return null;

        logger.info("Creating stack: {}", stack.getName());

        if(stack.preExecute!=null)
            stack.preExecute.call();

        CreateStackRequest createStackRequest = stack.prepareCreateRequest(this);
        CreateStackResult createStackResult = amazonCloudFormation.createStack(createStackRequest);
        String stackId = createStackResult.getStackId();
        //try {
        waitForCompletion(stack, "CREATE_COMPLETE", "ROLLBACK_");
        stack.setId(stackId);
            //logger.info("Stack created: {}", stack.getName());
//            }
//            catch (Exception e){
//                logger.error(e.getMessage());
//            }

        if(stack.postExecute!=null)
            stack.postExecute.call();


        return stack.getId();
    }

    public void deleteStack(AbstractStackHandler stack) throws Exception {
        //logger.debug("Checking stack {} before deletion", stack.getName());
        StackSummary stackSummary = findStackByName(stack.getName());
        if(stackSummary==null) {
            //logger.info("Stack " + stack.getName() + " not found");
        }else {
            logger.info("Deleting stack {}", stack.getName());
            DeleteStackRequest stackRequest = new DeleteStackRequest();
            stackRequest.setStackName(stackSummary.getStackName());
            amazonCloudFormation.deleteStack(stackRequest);
            waitForCompletion(stack, "DELETE_COMPLETE", "CREATE_COMPLETE");
        }

    }

//    public static void uploadToBucket(){
//        String bucketName = "testbucketswarm";
//        String fileObjKeyName = "someKey";
//        //amazonS3.createBucket(bucketName);
//
//        URL url = Resources.getResource("AWS/vpc-1azs.yaml");
//        File file = new File(url.getPath());
//
//        //PutObjectRequest request = new PutObjectRequest(bucketName, fileObjKeyName, "Value");
//        //amazonS3.putObject(bucketName, fileObjKeyName, "Value");
//        //amazonS3.putObject(bucketName, fileObjKeyName, "Value");
//
//        //S3Object ret = amazonS3.getObject(new GetObjectRequest(bucketName, fileObjKeyName));
//        //ret.getObjectContent();
//
////        AccessControlList acl = new AccessControlList();
////        acl.grantPermission(new CanonicalGrantee("http://acs.amazonaws.com/groups/global/AllUsers"), Permission.Read);
//
//        Collection<Grant> grantCollection = new ArrayList<Grant>();
//
//        Grant grant1 = new Grant(new CanonicalGrantee(amazonS3.getS3AccountOwner().getId()), Permission.FullControl);
//        grantCollection.add(grant1);
//
//        Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Read);
//        grantCollection.add(grant2);
//
//        AccessControlList bucketAcl = amazonS3.getBucketAcl(bucketName);
//        bucketAcl.getGrantsAsList().clear();
//        bucketAcl.getGrantsAsList().addAll(grantCollection);
//        amazonS3.setBucketAcl(bucketName, bucketAcl);
//
//        AccessControlList objectAcl = amazonS3.getObjectAcl(bucketName, fileObjKeyName);
//        objectAcl.getGrantsAsList().clear();
//        objectAcl.getGrantsAsList().addAll(grantCollection);
//        amazonS3.setObjectAcl(bucketName, fileObjKeyName, objectAcl);
//
//        //amazonS3.setObjectAcl(new SetObjectAclRequest(bucketName, fileObjKeyName, acl));
//
////        UploadPartRequest req = new UploadPartRequest();
////        req.setKey("somekey");
////        req.setBucketName(bucketName);
////        req.setFile(file);
////
////        amazonS3.uploadPart(req);
//        String test="123";
//        //amazonS3.copyPart()
//
//        //aws s3api get-object --bucket testbucketswarm --key someKey outfile --region=eu-central-1
//
//    }

}
