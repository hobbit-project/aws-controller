package org.hobbit.cloud.vpc.handlers;

import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class VPCStackHandler extends AbstractStackHandler {

    public VPCStackHandler(AbstractStackHandler.Builder builder){
        super(builder);
        name = builder.getName();
        bodyUrl = "https://s3-eu-west-1.amazonaws.com/widdix-aws-cf-templates/vpc/vpc-2azs.yaml";
    }



    public static class Builder<C extends AbstractStackHandler, B extends Builder<C,B>> extends AbstractStackHandler.Builder<C,B>{


    }
}
