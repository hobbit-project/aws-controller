package org.hobbit.cloud.interfaces;

import java.util.List;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */


public interface ICloudSwarmManager extends ICloudClusterManager {
    public List<Node> getManagerNodes() throws Exception;
    public List<Node> getBechmarkNodes() throws Exception;
    public List<Node> getSystemNodes() throws Exception;

    public String getClusterConfiguration();
    //public void createManagers(String configuration) throws Exception;
    //public void deleteManagers() throws Exception;
    //public void createWorkers(String configuration) throws Exception;
    //public void deleteWorkers() throws Exception;
}
