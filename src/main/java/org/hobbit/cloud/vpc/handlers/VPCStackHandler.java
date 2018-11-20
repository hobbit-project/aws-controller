package org.hobbit.cloud.vpc.handlers;

import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */

public class VPCStackHandler extends AbstractStackHandler {

    public VPCStackHandler(AbstractStackHandler.Builder builder){
        super(builder);
        name = builder.getName();
    }

    @Override
    public String getBodyUrl() {
        return "https://s3-eu-west-1.amazonaws.com/widdix-aws-cf-templates/vpc/vpc-2azs.yaml";
    }


}
