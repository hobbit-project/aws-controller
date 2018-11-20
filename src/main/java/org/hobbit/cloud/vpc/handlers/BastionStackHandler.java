package org.hobbit.cloud.vpc.handlers;


import org.hobbit.awscontroller.StackHandlers.SSHDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class BastionStackHandler extends SSHDependentStackHandler {


    public BastionStackHandler(SSHDependentStackHandler.Builder builder){
        super(builder);
        name = builder.getName();

    }

    @Override
    public String getBodyUrl(){
        return "https://s3-eu-west-1.amazonaws.com/widdix-aws-cf-templates/vpc/vpc-ssh-bastion.yaml";
    }

}
