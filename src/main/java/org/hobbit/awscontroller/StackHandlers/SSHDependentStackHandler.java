package org.hobbit.awscontroller.StackHandlers;

import com.amazonaws.services.cloudformation.model.Parameter;
//import org.hobbit.awscontroller.StackHandlers.Builders.SshKeyPairedStackBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class SSHDependentStackHandler extends VpcDependentStackHandler {


    public SSHDependentStackHandler(Builder builder) {
        super(builder);
        parameters.put("KeyName", builder.sshKeyName);

    }



    public static class Builder<C extends VpcDependentStackHandler, B extends Builder<C,B>> extends VpcDependentStackHandler.Builder<C,B>{

        protected String sshKeyName;

        public B sshKeyName(String value){
            sshKeyName = value;
            return (B)this;
        }

        public B name(String value){
            name = value;
            return (B)this;
        }

        public String getSshKeyName() {
            return sshKeyName;
        }

    }
}
