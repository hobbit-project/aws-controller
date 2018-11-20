package org.hobbit.awscontroller;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.profile.internal.AllProfiles;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.auth.profile.internal.ProfileAssumeRoleCredentialsProvider;
import com.amazonaws.auth.profile.internal.securitytoken.ProfileCredentialsService;
import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClientBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AWSCloudFormationTest {
    AmazonCloudFormation amazonCloudFormation;

    @Before
    public void init(){
        AwsCredentials.setAsEnvVariables();

        String region = System.getenv("AWS_REGION");
        AmazonCloudFormationClientBuilder amazonCloudFormationClientBuilder = AmazonCloudFormationClientBuilder.standard();
        amazonCloudFormationClientBuilder.setRegion(region);
        amazonCloudFormationClientBuilder.setCredentials(new EnvironmentVariableCredentialsProvider());


        Map<String, String> defaultProperties = new HashMap<>();
        defaultProperties.put("region", region);
        defaultProperties.put("aws_access_key_id", System.getenv("AWS_ACCESS_KEY_ID"));
        defaultProperties.put("aws_secret_access_key", System.getenv("AWS_SECRET_KEY"));

        BasicProfile defaultProfile = new BasicProfile("default", defaultProperties);

        Map<String, String> properties = new HashMap<>();
        properties.put("role_arn", System.getenv("AWS_ROLE_ARN"));
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

        amazonCloudFormationClientBuilder.setCredentials(credentialsProvider);
        amazonCloudFormation = amazonCloudFormationClientBuilder.build();

    }

    @Test
    public void listStacksTest(){
        amazonCloudFormation.listStacks();
        Assert.assertTrue(true);
    }

    @Test
    @Ignore
    public void createStackTest() throws IOException {


        //CreateStackRequest createStackRequest = new VPCStack().prepareCreateRequest();

        //amazonCloudFormation.createStack(createStackRequest);

//        ListStacksResult res = amazonCloudFormation.listStacks();
//        for(StackSummary stackSummary : res.getStackSummaries())
//            if (stackSummary.getStackName().equals(stackName))
//                if(!stackSummary.getStackStatus().equals("CREATE_IN_PROGRESS") && stackSummary.getStackStatus().equals("CREATE_IN_COMPLETE"))
//                    Assert.fail(stackName+" stack was not created");


        Assert.assertTrue(true);
        String test="123";

    }

    @Test
    @Ignore
    public void deleteStackTest(){

//        String stackName = NAME;
//
//        DeleteStackRequest stackRequest = new DeleteStackRequest();
//        stackRequest.setStackName(stackName);
//
//        amazonCloudFormation.deleteStack(stackRequest);
//
//        ListStacksResult res = amazonCloudFormation.listStacks();
//        for(StackSummary stackSummary : res.getStackSummaries())
//            if (stackSummary.getStackName().equals(stackName))
//                if(!stackSummary.getStackStatus().equals("DELETE_IN_PROGRESS") || !stackSummary.getStackStatus().equals("DELETE_IN_COMPLETE"))
//                    Assert.fail(stackName + " stack delete failed");

        String test="123";

    }
}
