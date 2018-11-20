package org.hobbit.awscontroller;

import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;

public interface Callback<AbstractStackHandler, String> {
    public String call(AbstractStackHandler stack);
}
