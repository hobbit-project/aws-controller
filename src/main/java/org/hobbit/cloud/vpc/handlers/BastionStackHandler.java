package org.hobbit.cloud.vpc.handlers;


import org.hobbit.awscontroller.StackHandlers.SSHDependentStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class BastionStackHandler extends SSHDependentStackHandler {


    public BastionStackHandler(SSHDependentStackHandler.Builder builder){
        super(builder);
        name = builder.getName();
        bodyUrl = "https://s3-eu-west-1.amazonaws.com/widdix-aws-cf-templates/vpc/vpc-ssh-bastion.yaml";
    }

    public static class Builder<C extends SSHDependentStackHandler, B extends Builder<C,B>> extends SSHDependentStackHandler.Builder<C,B>{

//        protected String sshKeyName;
//
//        public B sshKeyName(String value){
//            sshKeyName = value;
//            return (B)this;
//        }
//
//        public B name(String value){
//            name = value;
//            return (B)this;
//        }
//
//        public String getSshKeyName() {
//            return sshKeyName;
//        }

    }
}
