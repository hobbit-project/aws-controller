package org.hobbit.awscontroller.StackHandlers;

import com.amazonaws.services.cloudformation.model.StackResourceSummary;
import com.amazonaws.services.cloudformation.model.Tag;
import org.hobbit.awscontroller.AWSController;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
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


//    public String getBodyUrl(){
//        return bodyUrl;
//    }
//    public String getBodyFilePath(){
//        return bodyFilePath;
//    }

    public Callable preExecute;
    public Callable postExecute;

    public String getName(){ return name; }
    public String getId(){ return id; }
    public void setId(String value){
        id = value;
    }

    public String getBodyFromFile(String filename) throws IOException {
        String body = new String(Files.readAllBytes(Paths.get(filename)));
//        URL url = Resources.getResource(filename);
//        String body = Resources.toString(url, Charset.defaultCharset());
        return body;
    }

    public void init(AWSController awsController) throws Exception {

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


    public CreateStackRequest prepareCreateRequest(AWSController awsController) throws Exception{

        init(awsController);

        CreateStackRequest createStackRequest = new CreateStackRequest();
        createStackRequest.setStackName(getName());
        createStackRequest.setCapabilities(Arrays.asList(new String[]{ "CAPABILITY_IAM" }));

        if(bodyFilePath!=null){
            String body = getBodyFromFile(bodyFilePath);
            createStackRequest.setTemplateBody(body);
        }else if(bodyUrl!=null)
             createStackRequest.setTemplateURL(bodyUrl);
        else {
            throw new Exception("Stack body (file or URL) is not specified");
        }


        List tagsList = new ArrayList<Parameter>();
        for (String key : tags.keySet())
            tagsList.add(new Tag().withKey(key).withValue(tags.get(key)));
        createStackRequest.setTags(tagsList);


        List paramList = new ArrayList<Parameter>();
        Map<String, Map<String, String>> parentStacksResources = new HashMap<>();
        for (String key : parameters.keySet())
            if(parameters.get(key)!=null) {
                String value = parameters.get(key);
                if(value.startsWith("${")){
                    String[] splitted = value.split("}.");
                    String parentStackName = splitted[0].substring(2);
                    String parentResourceKey = splitted[1];

                    String type="";
                    if(parentResourceKey.toLowerCase().startsWith("resources")){
                        type = ".resources";
                        parentResourceKey = parentResourceKey.substring(10);
                    }

                    if(parentResourceKey.toLowerCase().startsWith("outputs")){
                        type=".outputs";
                        parentResourceKey = parentResourceKey.substring(8);
                    }

                    if (!parentStacksResources.containsKey(parentStackName+type)) {
                        Map<String, String> values=null;
                        if(type.equals(".outputs"))
                            values = awsController.getStackOutputsMap(parentStackName);
                        else if(type.equals(".resources"))
                            values = awsController.getStackResourcesMap(parentStackName);
                        if(values==null)
                            throw new Exception(value+" cannot be imported");
                        parentStacksResources.put(parentStackName+type, values);
                    }
                    value = parentStacksResources.get(parentStackName+type).get(parentResourceKey);
                    if(value==null)
                        throw new Exception(parameters.get(key)+ " is null");

                }

                paramList.add(new Parameter().withParameterKey(key).withParameterValue(value));
            }

        createStackRequest.setParameters(paramList);
        return createStackRequest;
    }

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
