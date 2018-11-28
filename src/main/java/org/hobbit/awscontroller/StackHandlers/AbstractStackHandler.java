package org.hobbit.awscontroller.StackHandlers;

import com.amazonaws.services.cloudformation.model.*;
import org.hobbit.awscontroller.AWSController;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;

public abstract class AbstractStackHandler {
    private Logger logger;

    private String id;
    protected String name;
    protected Map<String, String> parameters;
    protected Map<String, String> resources;
    protected Map<String, String> tags;
    protected String bodyUrl;
    protected String bodyFilePath;

    public AbstractStackHandler(Builder builder){
        name = builder.name;
        parameters = (builder.parameters!=null?builder.parameters:new HashMap<>());

        tags = new HashMap<>();
        if(builder.tags!=null) {
            String[] items = builder.tags.split(";");
            for (String item : items) {
                String[] splitted = item.split("=");
                tags.put(splitted[0], splitted[1]);
            }
        }

    }

    public void setBodyUrl(String value){
        bodyUrl = value;
    }

    public void setBodyFilePath(String value){
        bodyFilePath = value;
    }


    public String getBodyUrl(){
        return bodyUrl;
    }
    public String getBodyFilePath(){
        return bodyFilePath;
    }

    public Callable preExecute;
    public Callable postExecute;

    public String getName(){ return name; }
    public String getId(){ return id; }
    public void setId(String value){
        id = value;
    }



    public Map<String, String> getParameters(){
        return parameters;
    };

    public Map<String, String> getResources(){
        return resources;
    };

    public AbstractStackHandler setTag(String key, String value) {

        tags.put(key, value);
        return this;

    }

    public AbstractStackHandler setName(String value) {

        name = value;
        return this;

    }

    public Map<String, String> getTags(){
        return tags;
    };



    public AbstractStackHandler appendParameters(Map<String, String> value){
        if(value!=null)
            parameters.putAll(value);
        return this;
    }

    public AbstractStackHandler setParameter(String key, String value){
        parameters.put(key, value);
        return this;
    }

    public AbstractStackHandler setResources(List<StackResourceSummary> stackResourceSummaries){
        resources = new HashMap<>();
        for(StackResourceSummary summary : stackResourceSummaries)
            resources.put(summary.getLogicalResourceId(), summary.getPhysicalResourceId());
        return this;
    }

//    public String create() throws Exception {
//
//        String ret = AWSController.createStack(this);
//        return ret;
//    }
//
//    public void delete() throws Exception {
//        AWSController.deleteStack(this);
//    }

    //public void prepareDeleteRequest();


    public abstract static class Builder<C extends AbstractStackHandler, B extends Builder<C,B>> {


        protected String name;
        protected Map<String, String> parameters;
        protected String tags;

//        public Builder(AbstractStackHandler copyFrom){
//            this.name = copyFrom.name;
//            this.parameters = copyFrom.parameters;
//        }

        public B name(String value) {
            this.name = value;
            return (B) this;
        }
//
        public B parameters(Map value) {
            this.parameters = value;
            return (B) this;
        }

        public String getName(){
            return this.name;
        }

        public B tags(String value){
            return (B) this;
        }
//
//        //public abstract C build() throws Exception;
////        public C build() throws Exception{
////            return obj;
////        };
//        protected abstract C createObj();
//        protected abstract B getThis();

    }
}
