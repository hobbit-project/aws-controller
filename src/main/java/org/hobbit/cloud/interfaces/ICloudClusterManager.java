package org.hobbit.cloud.interfaces;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */


public interface ICloudClusterManager {
    public Resource getVPC() throws Exception;
    public Node getBastion() throws Exception;
    public Node getNAT() throws Exception;

    public void createCluster(String configuration) throws Exception;
    public void deleteCluster(String configuration) throws Exception;
    public void createCluster() throws Exception;
    public void deleteCluster() throws Exception;


}
