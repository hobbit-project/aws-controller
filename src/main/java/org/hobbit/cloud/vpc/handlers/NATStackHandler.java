package org.hobbit.cloud.vpc.handlers;

import org.hobbit.awscontroller.StackHandlers.SSHDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class NATStackHandler extends SSHDependentStackHandler {

    public NATStackHandler(SSHDependentStackHandler.Builder builder) {
        super(builder);
        name = builder.getName();
    }


    @Override
    public String getBodyUrl() {
        return "https://s3-eu-west-1.amazonaws.com/widdix-aws-cf-templates/vpc/vpc-nat-instance.yaml";
    }


}
