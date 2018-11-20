package org.hobbit.cloud.interfaces;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */


public class Resource {
    private String id;

    public String getId(){
        return id;
    }

    public Resource(String id){
        this.id = id;
    }
}
