package org.hobbit.cloud.interfaces;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */


public class Node extends Resource {

    private String publicIp;
    private String internalIp;

    public Node(String name){
        super(name);
    }

    public Node setPublicIpAddress(String value){
        publicIp = value;
        return this;
    }

    public Node setPrivateIpAddress(String value){
        internalIp = value;
        return this;
    }

    public String getPublicIpAddress(){
        return publicIp;
    }
    public String getIngernalIpAddress(){
        return internalIp;
    }
}
