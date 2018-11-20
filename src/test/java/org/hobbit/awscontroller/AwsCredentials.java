package org.hobbit.awscontroller;

import org.junit.contrib.java.lang.system.EnvironmentVariables;

public class AwsCredentials {

    public static void setAsEnvVariables(){
        EnvironmentVariables environmentVariables = new EnvironmentVariables();
        environmentVariables.set("AWS_ACCESS_KEY_ID", "");
        environmentVariables.set("AWS_SECRET_KEY", "");
        environmentVariables.set("AWS_ROLE_ARN", "");
        environmentVariables.set("AWS_REGION", "");

    }
}
