package org.hobbit.awscontroller;

import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2AsyncClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.kms.model.NotFoundException;
import com.amazonaws.services.neptune.AmazonNeptune;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    public static ExecutorService es = Executors.newFixedThreadPool(4);
    Semaphore finishedMutex = new Semaphore(0);
    AWSCredentialsProvider credentialsProvider;
    //private ProfileAssumeRoleCredentialsProvider credentialsProvider;

    public AWSController(){

        if (!System.getenv().containsKey("AWS_ACCESS_KEY_ID"))
            logger.error("AWS_ACCESS_KEY_ID is missing");

        if (!System.getenv().containsKey("AWS_SECRET_KEY"))
            logger.error("AWS_SECRET_KEY is missing");


        if (!System.getenv().containsKey("AWS_ROLE_ARN"))
            logger.error("AWS_ROLE_ARN is missing");


        if (!System.getenv().containsKey("AWS_REGION"))
            logger.error("AWS_REGION is missing");

        aws_access_key_id = System.getenv("AWS_ACCESS_KEY_ID");
        aws_secret_key = System.getenv("AWS_SECRET_KEY");
        aws_role_arn = System.getenv("AWS_ROLE_ARN");
        aws_region = System.getenv("AWS_REGION");
    }

    public AWSController(String aws_access_key_id, String aws_secret_key, String aws_role_arn, String aws_region){
        this.aws_access_key_id = aws_access_key_id;
        this.aws_secret_key = aws_secret_key;
        this.aws_role_arn = aws_role_arn;
        this.aws_region = aws_region;
    }


    private AWSCredentialsProvider getCredentialsProvider() throws Exception {

        if(credentialsProvider==null) {

            Map<String, String> defaultProfileProperties = new HashMap<>();
            defaultProfileProperties.put("region", aws_region);
            defaultProfileProperties.put("aws_access_key_id", aws_access_key_id);
            defaultProfileProperties.put("aws_secret_access_key", aws_secret_key);

            BasicProfile defaultProfile = new BasicProfile("default", defaultProfileProperties);

            Map<String, String> properties = new HashMap<>();
            properties.put("role_arn", aws_role_arn);
            properties.put("source_profile", "default");

            BasicProfile roleProfile = new BasicProfile("roleProfile", properties);

            Map<String, BasicProfile> allProfiles = new HashMap<>();
            allProfiles.put(defaultProfile.getProfileName(), defaultProfile);
            allProfiles.put(roleProfile.getProfileName(), roleProfile);

            credentialsProvider = new ProfileAssumeRoleCredentialsProvider(
                    new ProfileCredentialsService() {
                        @Override
                        public AWSCredentialsProvider getAssumeRoleCredentialsProvider(RoleInfo roleInfo) {
                            return new STSProfileCredentialsServiceProvider(roleInfo);
                        }
                    }, new AllProfiles(allProfiles), roleProfile
            );

        }
        return credentialsProvider;

    }



 //        AmazonAutoScalingClientBuilder amazonAutoScalingBuilder = AmazonAutoScalingClientBuilder.standard();
//        amazonAutoScalingBuilder.setRegion(AWS_REGION_EU);
//        amazonAutoScalingBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
//        amazonAutoScalingBuilder.setCredentials(credentialsProvider);
//        amazonAutoScaling = amazonAutoScalingBuilder.build();

    public AmazonCloudFormation getAmazonCloudFormation() throws Exception {
        if(amazonCloudFormation==null) {
            AmazonCloudFormationClientBuilder amazonCloudFormationClientBuilder = AmazonCloudFormationClientBuilder.standard();
            amazonCloudFormationClientBuilder.setRegion(aws_region);
            amazonCloudFormationClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
            amazonCloudFormationClientBuilder.setCredentials(getCredentialsProvider());
            amazonCloudFormation = amazonCloudFormationClientBuilder.build();
        }
        return amazonCloudFormation;
    }

    public AmazonAutoScaling getAmazonAutoScaling() throws Exception {
        if(amazonAutoScaling==null) {
            AmazonAutoScalingClientBuilder amazonAutoScalingClientBuilder = AmazonAutoScalingClientBuilder.standard();
            amazonAutoScalingClientBuilder.setRegion(aws_region);
            amazonAutoScalingClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
            amazonAutoScalingClientBuilder.setCredentials(getCredentialsProvider());
            amazonAutoScaling = amazonAutoScalingClientBuilder.build();
        }
        return amazonAutoScaling;

    }

    public AmazonEC2 getAmazonEC2() throws Exception {
        if(amazonEC2==null) {
            AmazonEC2AsyncClientBuilder amazonEC2AsyncClientBuilder = AmazonEC2AsyncClientBuilder.standard();
            amazonEC2AsyncClientBuilder.setRegion(aws_region);
            amazonEC2AsyncClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
            amazonEC2AsyncClientBuilder.setCredentials(getCredentialsProvider());
            amazonEC2 = amazonEC2AsyncClientBuilder.build();
        }
        return amazonEC2;
    }

    public AmazonS3 getAmazonS3() throws Exception {
        if(amazonS3==null) {
            AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder.standard();
            amazonS3ClientBuilder.setRegion(aws_region);
            amazonS3ClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
            amazonS3ClientBuilder.setCredentials(getCredentialsProvider());
            amazonS3 = amazonS3ClientBuilder.build();
        }
        return amazonS3;
    }

    public AmazonNeptune getAmazonNeptune() throws Exception {
        AmazonNeptuneClientBuilder amazonNeptuneClientBuilder = AmazonNeptuneClientBuilder.standard();
        amazonNeptuneClientBuilder.setRegion(aws_region);
        amazonNeptuneClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());
        amazonNeptuneClientBuilder.setCredentials(getCredentialsProvider());
        amazonNeptune = amazonNeptuneClientBuilder.build();
        return amazonNeptune;
    }

    public StackSummary findStackByName(String name) throws Exception{
        List<StackSummary> ret = new ArrayList<>();
        ListStacksResult result;
        String nextToken = "";

        while(ret.size()==0 && nextToken!=null){

            if(nextToken.equals(""))
                result = getAmazonCloudFormation().listStacks();
            else{
                ListStacksRequest listStacksRequest = new ListStacksRequest();
                listStacksRequest.setNextToken(nextToken);
                result = getAmazonCloudFormation().listStacks(listStacksRequest);
            }
            nextToken = result.getNextToken();
            ret = result.getStackSummaries().stream().filter(
                    //s->s.getStackName().equals(name) && (s.getStackStatus().endsWith("_COMPLETE") || s.getStackStatus().endsWith("_IN_PROGRESS") || s.getStackStatus().endsWith("_FAILED"))
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
        getAmazonNeptune().addRoleToDBCluster(request);
    }

    public StackSummary findStackById(String id) throws Exception{
        List<StackSummary> ret = new ArrayList<>();
        ListStacksResult result;
        String nextToken = "";

        while(ret.size()==0 && nextToken!=null){

            if(nextToken.equals(""))
                result = getAmazonCloudFormation().listStacks();
            else{
                ListStacksRequest listStacksRequest = new ListStacksRequest();
                listStacksRequest.setNextToken(nextToken);
                result = getAmazonCloudFormation().listStacks(listStacksRequest);
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
           ListStackResourcesResult result = getAmazonCloudFormation().listStackResources(listStackResourcesRequest);
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
            DescribeStacksResult  result = getAmazonCloudFormation().describeStacks(request);
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
        DescribeAutoScalingGroupsResult result = getAmazonAutoScaling().describeAutoScalingGroups(request);
        List<AutoScalingGroup>  list = result.getAutoScalingGroups();
        return list;
    }

    public List<Instance> getEC2InstanceByName(String name) throws Exception{
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Arrays.asList(new String[]{ name }));
        DescribeInstancesResult result = getAmazonEC2().describeInstances(request);
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

    public void waitForCompletion(AbstractStackHandler stack, String until, String throwExceptionThen) throws Exception {
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
            } else if (throwExceptionThen != null && stackSummary.getStackStatus().startsWith(throwExceptionThen)) {
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
//        if(getAmazonS3().listBuckets().stream().filter(bucket -> bucket.getName().equals(bucketName)).count()>0) {
//            logger.info("Bucket {} already created", bucketName);
//
//        }else {
//            CreateBucketRequest request = new CreateBucketRequest(bucketName, AWS_REGION_EU);
//
//            AccessControlList acl = new AccessControlList();
//            acl.grantPermission(GroupGrantee.AllUsers, Permission.Write);
//
//            request.setAccessControlList(acl);
//            Bucket result = getAmazonS3().createBucket(request);
//            logger.info("Bucket {} created", bucketName);
//        }
//    }
    public void createBucket(String bucketName) throws Exception {
        createBucket(bucketName, null);
    }

    public void createBucket(String bucketName, String region) throws Exception {
        if(region==null)
            region = aws_region;
        if(getAmazonS3().listBuckets().stream().filter(bucket -> bucket.getName().equals(bucketName)).count()>0) {
            logger.info("Bucket {} already created", bucketName);

        }else {
            CreateBucketRequest request = new CreateBucketRequest(bucketName, region);

            //AccessControlList acl = new AccessControlList();
            //acl.grantPermission(GroupGrantee.AllUsers, Permission.Write);

            //request.setAccessControlList(acl);
            Bucket result = getAmazonS3().createBucket(request);
            logger.info("Bucket {} created", bucketName);
        }
    }

    public void putObjectToS3(String bucketName, File file) throws Exception{
        PutObjectRequest request = new PutObjectRequest(bucketName, file.getName() ,file);
        PutObjectResult result = getAmazonS3().putObject(request);
    }

    public void switchBucketAvailability(String bucketName, Boolean publicWrite) throws Exception {
        logger.info("Switching bucket availability (publicWrite={}) for bucket {} ", publicWrite, bucketName);
        Collection<Grant> grantCollection = new ArrayList<Grant>();

        Grant grant1 = new Grant(new CanonicalGrantee(getAmazonS3().getS3AccountOwner().getId()), Permission.FullControl);
        grantCollection.add(grant1);

        if(publicWrite) {
            Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Write);
            grantCollection.add(grant2);
        }

        AccessControlList acl = getAmazonS3().getBucketAcl(bucketName);
        acl.getGrantsAsList().clear();
        acl.getGrantsAsList().addAll(grantCollection);
        getAmazonS3().setBucketAcl(bucketName, acl);
    }

    public void setPublicAvailabilityToS3File(String bucketName, String fileKey) throws Exception {

        logger.info("Setting Public Availability To S3 File {} (bucket={})", fileKey, bucketName);
        Collection<Grant> grantCollection = new ArrayList<Grant>();

        Grant grant1 = new Grant(new CanonicalGrantee(getAmazonS3().getS3AccountOwner().getId()), Permission.FullControl);
        grantCollection.add(grant1);

        Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Read);
        grantCollection.add(grant2);

        AccessControlList objectAcl = getAmazonS3().getObjectAcl(bucketName, fileKey);
        objectAcl.getGrantsAsList().clear();
        objectAcl.getGrantsAsList().addAll(grantCollection);
        getAmazonS3().setObjectAcl(bucketName, fileKey, objectAcl);
    }

//    public void createStacksSequental(List<AbstractStackHandler> stackList) throws Exception {
//        for (AbstractStackHandler stack : stackList){
//            createStack(stack);
//        }
//    }

    public List<String> createStacks(List<List<AbstractStackHandler>> hierarchicalStackList) throws Exception {
        return createStacks(hierarchicalStackList, false);
    }

    public List<String> createStacks(List<List<AbstractStackHandler>> hierarchicalStackList, boolean allowStacksUpdate) throws Exception {

//        if(allowStacksUpdate){
//            List<List<AbstractStackHandler>> stackToUpdate = findStacksToUpdate(hierarchicalStackList);
//            updateStacks(stackToUpdate);
//        }

        List<String> newlyCreatedStackIds = new ArrayList<>();
        processStacksByLevels(hierarchicalStackList, new Callback<AbstractStackHandler, String>(){
            @Override
            public String call(AbstractStackHandler stack){
                String stackId = null;
                //while (stackId == null){
                //while(stackId==null) {
                    try {
                        stackId = createStack(stack, allowStacksUpdate);
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

//    public List<List<AbstractStackHandler>> findStacksToUpdate(List<List<AbstractStackHandler>> hierarchicalStackList){
//        logger.info("Checking already existing stacks");
//
//        List<List<AbstractStackHandler>> ret = new ArrayList<>();
//        List<String> names = new ArrayList<>();
//
//        for (List<AbstractStackHandler> level: hierarchicalStackList){
//            List<AbstractStackHandler> stacksToUpdateOnTheLevel = new ArrayList<>();
//            Map<AbstractStackHandler, Integer> stacksToAlterOnTheLevel = new HashMap<>();
//            for (AbstractStackHandler stack : level){
//                try {
//                    StackSummary summary = findStackByName(stack.getName());
//                    if (!summary.getStackStatus().contains("PROGRESS")){
//                        List<Parameter> parameters = getStackParameters(stack.getName());
//                        boolean requiresUpdate = false;
//                        if (parameters != null) {
//                            Map<String, String> existingStackParams = new HashMap<>();
//                            for (Parameter parameter : parameters)
//                                existingStackParams.put(parameter.getParameterKey(), parameter.getParameterValue());
//
//                            for (String paramName : stack.getParameters().keySet()) {
//                                String exisingParamValue = existingStackParams.get(paramName);
//                                String desiredParamValue = stack.getParameters().get(paramName);
//                                if (!existingStackParams.containsKey(paramName) || (!exisingParamValue.equals("****") && !existingStackParams.get(paramName).equals(desiredParamValue))) {
//                                    requiresUpdate = true;
//                                    logger.info("Param {}={}, not {} for the {} stack", paramName, exisingParamValue, desiredParamValue, stack.getName());
//                                }
//                            }
//
//                        } else
//                            requiresUpdate = true;
//
//                        if (requiresUpdate){
//                            logger.info("Stack {} requires update with new parameters. The existing stack will be deleted", stack.getName());
//                            stacksToUpdateOnTheLevel.add(stack);
//                            names.add(stack.getName());
//                        }
//                    }
//                }
//                catch (NotFoundException e){
//                    //logger.error("");
//                }
//                catch (Exception e){
//                    //logger.error("Problem with getting stack parameters: {}", stack.getName());
//                }
//
//            }
//            if(stacksToUpdateOnTheLevel.size()>0)
//                ret.add(stacksToUpdateOnTheLevel);
//        }
//        if(names.size()>0)
//            logger.info("The following stacks will be updated: {}", String.join(",", names));
//        return ret;
//    }

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

    public void updateStacks(List<List<AbstractStackHandler>> hierarchicalStackList) throws Exception {

        processStacksByLevels(Lists.reverse(hierarchicalStackList), new Callback<AbstractStackHandler, String>(){
            @Override
            public String call(AbstractStackHandler stack){
                //while (stackId == null){
                try {
                    updateStack(stack);
                    logger.debug("Stack {} was updated", stack.getName());
                } catch (Exception e) {
                    logger.error("Stack {} was not updated: {}", stack.getName(), e.getMessage());
                    //logger.info("Trying to recreate the stack {}", stack.getName());
                }
                return null;
            }
        });

    }

    private void processStacksByLevels(List<List<AbstractStackHandler>> hierarchicalStackList, Callback<AbstractStackHandler, String> action) throws Exception {

        int levelIndex=0;
        for (List<AbstractStackHandler> level: hierarchicalStackList){
            levelIndex++;
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
                logger.info("Level {} finished in {} s ({})", levelIndex, took, String.join("_", layerStackNames));
        }
    }

    public List<Parameter> getStackParameters(String stackName) throws Exception{
        DescribeStacksRequest req = new DescribeStacksRequest();
        req.setStackName(stackName);
        DescribeStacksResult res = getAmazonCloudFormation().describeStacks(req);
        if(res.getStacks().size()>0)
            return res.getStacks().get(0).getParameters();
        return null;
    }

    public String createStack(AbstractStackHandler stack) throws Exception {
        return createStack(stack, false);
    }

    public String createStack(AbstractStackHandler stack, boolean allowUpdate) throws Exception {
        StackSummary stackSummary = findStackByName(stack.getName());
        if(stackSummary!=null){
          if(allowUpdate){
              if (!stackSummary.getStackStatus().contains("PROGRESS")){
                  List<Parameter> parameters = getStackParameters(stack.getName());
                  boolean requiresUpdate = false;
                  if (parameters != null) {
                      Map<String, String> existingStackParams = new HashMap<>();
                      for (Parameter parameter : parameters)
                          existingStackParams.put(parameter.getParameterKey(), parameter.getParameterValue());

                      for (String paramName : stack.getParameters().keySet()) {
                          String exisingParamValue = existingStackParams.get(paramName);
                          String desiredParamValue = stack.getParameters().get(paramName);
                          if (!existingStackParams.containsKey(paramName) || (!exisingParamValue.equals("****") && !existingStackParams.get(paramName).equals(desiredParamValue))){
                              if(paramName.equals("MaxSize")){
                                  List<StackResourceSummary> asgs = getStackResources(stack.getName(), "AWS::AutoScaling::AutoScalingGroup");
                                  if(asgs.size()>0){
                                      int runningMachines = getEC2InstancesByAutoscalingGroupName(asgs.get(0).getPhysicalResourceId()).size();
                                      int valueToSet = Math.max(Integer.parseInt(desiredParamValue), runningMachines);
                                      stack.getParameters().put(paramName, String.valueOf(valueToSet));
                                  }
                              }

                              requiresUpdate = true;
                              logger.info("Param {}={}, not {} for the {} stack", paramName, exisingParamValue, desiredParamValue, stack.getName());
                          }
                      }

                  } else
                      requiresUpdate = true;

                  if (requiresUpdate){
                      updateStack(stack);
                      return stack.getId();
                  }
              }
          }

          if (stackSummary.getStackStatus().startsWith("CREATE_")){
                if(stackSummary.getStackStatus().equals("CREATE_IN_PROGRESS"))
                    waitForCompletion(stack, "CREATE_COMPLETE");
                stack.setId(stackSummary.getStackId());
                logger.info("Stack {} created", stack.getName());
            } else if (stackSummary.getStackStatus().contains("ROLLBACK_")) {
                  //if(stackSummary.getStackStatus().endsWith("ROLLBACK_FAILED")) {
                      logger.info("A rollbacked stack found. Deleting: {}", stack.getName());
                      deleteStack(stack);
                  //}
            } else if (stackSummary.getStackStatus().startsWith("UPDATE_")) {
                  if(stackSummary.getStackStatus().endsWith("_FAILED")) {
                      logger.info("A rollbacked stack found. Deleting: {}", stack.getName());
                      deleteStack(stack);
                  }
                stack.setId(stackSummary.getStackId());
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

        CreateStackRequest createStackRequest = prepareCreateRequest(stack);
        CreateStackResult createStackResult = getAmazonCloudFormation().createStack(createStackRequest);
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

    public void updateStack(AbstractStackHandler stack) throws Exception {
        StackSummary stackSummary = findStackByName(stack.getName());
        Boolean creationRequired = false;
        if(stackSummary!=null){
            if (stackSummary.getStackStatus().startsWith("CREATE_")){
                if(stackSummary.getStackStatus().equals("CREATE_IN_PROGRESS"))
                    waitForCompletion(stack, "CREATE_COMPLETE");
                logger.info("Stack {} updated", stack.getName());
            } else if (stackSummary.getStackStatus().startsWith("UPDATE_")){
                if(stackSummary.getStackStatus().equals("UPDATE_IN_PROGRESS")) {
                    waitForCompletion(stack, "UPDATE_COMPLETE");
                    logger.info("Stack {} updated", stack.getName());
                }else if (stackSummary.getStackStatus().contains("ROLLBACK_")) {
                    logger.info("A rollbacked stack found. Deleting: {}", stack.getName());
                    deleteStack(stack);
                    logger.info("Stack {} deleted and would be recreated", stack.getName());
                    creationRequired = true;
                }
            }  else if (stackSummary.getStackStatus().equals("DELETE_IN_PROGRESS")) {
                logger.info("A stack {} with DELETE_IN_PROGRESS found", stack.getName());
                waitForCompletion(stack, "DELETE_COMPLETE");
                logger.info("Stack {} deleted and would be recreated", stack.getName());
                creationRequired = true;
            }
        }

        if(stack.getId()!=null)
            return;

        if(creationRequired){
            createStack(stack);
            return;
        }

        logger.info("Updating stack: {}", stack.getName());

        if(stack.preExecute!=null)
            stack.preExecute.call();

        UpdateStackRequest updateStackRequest = prepareUpdateRequest(stack);
        try {
            UpdateStackResult updateStackResult = getAmazonCloudFormation().updateStack(updateStackRequest);
            waitForCompletion(stack, "UPDATE_COMPLETE", "UPDATE_ROLLBACK_");

            if (stack.postExecute != null)
                stack.postExecute.call();
        }
        catch (Exception e){
            logger.error("Failed to update stack {}: {}", stack.getName(), e.getLocalizedMessage());
            e.printStackTrace();
            throw e;
        }
        return;
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
            getAmazonCloudFormation().deleteStack(stackRequest);
            waitForCompletion(stack, "DELETE_COMPLETE", "CREATE_COMPLETE");
        }
    }

    public CreateStackRequest prepareCreateRequest(AbstractStackHandler stack) throws Exception{

        CreateStackRequest createStackRequest = new CreateStackRequest();
        createStackRequest.setStackName(stack.getName());
        createStackRequest.setCapabilities(Arrays.asList(new String[]{ "CAPABILITY_IAM" }));

        if(stack.getBodyFilePath()!=null){
            createStackRequest.setTemplateBody(readBodyFromFile(stack.getBodyFilePath()));
        }else if(stack.getBodyUrl()!=null)
            createStackRequest.setTemplateURL(stack.getBodyUrl());
        else {
            throw new Exception("Stack body (file or URL) is not specified");
        }


        List tagsList = new ArrayList<Parameter>();
        Map<String, String> tags = stack.getTags();
        for (String key : tags.keySet())
            tagsList.add(new Tag().withKey(key).withValue(tags.get(key)));
        createStackRequest.setTags(tagsList);


        List<Parameter>  paramList = createParamsList(stack);
        createStackRequest.setParameters(paramList);

        return createStackRequest;
    }

    public UpdateStackRequest prepareUpdateRequest(AbstractStackHandler stack) throws Exception{

        UpdateStackRequest updateStackRequest = new UpdateStackRequest();
        updateStackRequest.setStackName(stack.getName());
        updateStackRequest.setCapabilities(Arrays.asList(new String[]{ "CAPABILITY_IAM" }));

        if(stack.getBodyFilePath()!=null){
            updateStackRequest.setTemplateBody(readBodyFromFile(stack.getBodyFilePath()));
        }else if(stack.getBodyUrl()!=null)
            updateStackRequest.setTemplateURL(stack.getBodyUrl());
        else {
            throw new Exception("Stack body (file or URL) is not specified");
        }

        List tagsList = new ArrayList<Parameter>();
        Map<String, String> tags = stack.getTags();
        for (String key : tags.keySet())
            tagsList.add(new Tag().withKey(key).withValue(tags.get(key)));
        updateStackRequest.setTags(tagsList);

        List<Parameter>  paramList = createParamsList(stack);
        updateStackRequest.setParameters(paramList);

        return updateStackRequest;
    }

    private String readBodyFromFile(String filename) throws IOException {
        String body = new String(Files.readAllBytes(Paths.get(filename)));
//        URL url = Resources.getResource(filename);
//        String body = Resources.toString(url, Charset.defaultCharset());
        return body;
    }

    private List<Parameter> createParamsList(AbstractStackHandler stack) throws Exception {
        List paramList = new ArrayList<Parameter>();
        Map<String, Map<String, String>> parentStacksResources = new HashMap<>();
        Map<String, String> parameters = stack.getParameters();
        for (String paramName : parameters.keySet())
            if(parameters.get(paramName)!=null) {
                String value = parameters.get(paramName);
                if(value.startsWith("${")){
                    String[] splitted = value.split("}.");
                    String parentStackName = splitted[0].substring(2);
                    String parentResourceKey = splitted[1];

                    String type="";
                    if(parentResourceKey.toLowerCase().startsWith("resources")){
                        type = ".resources";
                        parentResourceKey = parentResourceKey.substring(10);
                    }

                    if(parentResourceKey.toLowerCase().startsWith("outputs")){
                        type=".outputs";
                        parentResourceKey = parentResourceKey.substring(8);
                    }

                    if (!parentStacksResources.containsKey(parentStackName+type)) {
                        Map<String, String> values=null;
                        if(type.equals(".outputs"))
                            values = getStackOutputsMap(parentStackName);
                        else if(type.equals(".resources"))
                            values = getStackResourcesMap(parentStackName);
                        if(values==null)
                            throw new Exception(value+" cannot be imported");
                        parentStacksResources.put(parentStackName+type, values);
                    }
                    value = parentStacksResources.get(parentStackName+type).get(parentResourceKey);
                    if(value==null)
                        throw new Exception(parameters.get(paramName)+ " is null");

                }

                paramList.add(new Parameter().withParameterKey(paramName).withParameterValue(value));
            }
        return paramList;
    }

//    public static void uploadToBucket(){
//        String bucketName = "testbucketswarm";
//        String fileObjKeyName = "someKey";
//        //getAmazonS3().createBucket(bucketName);
//
//        URL url = Resources.getResource("AWS/vpc-1azs.yaml");
//        File file = new File(url.getPath());
//
//        //PutObjectRequest request = new PutObjectRequest(bucketName, fileObjKeyName, "Value");
//        //getAmazonS3().putObject(bucketName, fileObjKeyName, "Value");
//        //getAmazonS3().putObject(bucketName, fileObjKeyName, "Value");
//
//        //S3Object ret = getAmazonS3().getObject(new GetObjectRequest(bucketName, fileObjKeyName));
//        //ret.getObjectContent();
//
////        AccessControlList acl = new AccessControlList();
////        acl.grantPermission(new CanonicalGrantee("http://acs.amazonaws.com/groups/global/AllUsers"), Permission.Read);
//
//        Collection<Grant> grantCollection = new ArrayList<Grant>();
//
//        Grant grant1 = new Grant(new CanonicalGrantee(getAmazonS3().getS3AccountOwner().getId()), Permission.FullControl);
//        grantCollection.add(grant1);
//
//        Grant grant2 = new Grant(GroupGrantee.AllUsers, Permission.Read);
//        grantCollection.add(grant2);
//
//        AccessControlList bucketAcl = getAmazonS3().getBucketAcl(bucketName);
//        bucketAcl.getGrantsAsList().clear();
//        bucketAcl.getGrantsAsList().addAll(grantCollection);
//        getAmazonS3().setBucketAcl(bucketName, bucketAcl);
//
//        AccessControlList objectAcl = getAmazonS3().getObjectAcl(bucketName, fileObjKeyName);
//        objectAcl.getGrantsAsList().clear();
//        objectAcl.getGrantsAsList().addAll(grantCollection);
//        getAmazonS3().setObjectAcl(bucketName, fileObjKeyName, objectAcl);
//
//        //getAmazonS3().setObjectAcl(new SetObjectAclRequest(bucketName, fileObjKeyName, acl));
//
////        UploadPartRequest req = new UploadPartRequest();
////        req.setKey("somekey");
////        req.setBucketName(bucketName);
////        req.setFile(file);
////
////        getAmazonS3().uploadPart(req);
//        String test="123";
//        //getAmazonS3().copyPart()
//
//        //aws s3api get-object --bucket testbucketswarm --key someKey outfile --region=eu-central-1
//
//    }

}

