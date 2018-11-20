package org.hobbit.awscontroller;

import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;
import org.hobbit.cloud.vpc.VpcClusterManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class VpcClusterManagerTest {

    VpcClusterManager vpcClusterManager;

    @Before
    public void init() throws Exception {

        vpcClusterManager = new VpcClusterManager("hobbit","hobbit.pem");
    }


    @Test
    public void createClusterTest() throws Exception {
        vpcClusterManager.createCluster();
        Assert.assertTrue(true);
    }

    @Test
    public void deleteClusterTest() throws Exception {
        vpcClusterManager.deleteCluster();
        Assert.assertTrue(true);

    }

}
