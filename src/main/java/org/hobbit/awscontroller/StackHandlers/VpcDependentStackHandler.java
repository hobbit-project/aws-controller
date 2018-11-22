package org.hobbit.awscontroller.StackHandlers;


public class VpcDependentStackHandler extends AbstractStackHandler {

    protected String vpcStackName;

    public VpcDependentStackHandler(Builder builder) {
        super(builder);
        vpcStackName = builder.vpcStackName;
        parameters.put("ParentVPCStack", builder.vpcStackName);

    }

    public abstract static class Builder <C extends AbstractStackHandler, B extends Builder<C,B>> extends AbstractStackHandler.Builder<C,B>{

        protected String vpcStackName;

        public String getVpcStackName() {
            return vpcStackName;
        }

        public B vpcStackName(String value){
            vpcStackName = value;
            return (B)this;
        }

        public B name(String value){
            name = value;
            return (B)this;
        }

    }


}
