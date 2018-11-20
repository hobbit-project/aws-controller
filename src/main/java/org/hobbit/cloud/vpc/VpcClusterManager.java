package org.hobbit.cloud.vpc;

import com.amazonaws.services.cloudformation.model.StackResourceSummary;
import com.amazonaws.services.ec2.model.Instance;
import org.hobbit.awscontroller.AWSController;
import org.hobbit.awscontroller.StackHandlers.AbstractStackHandler;
import org.hobbit.cloud.vpc.handlers.BastionStackHandler;
import org.hobbit.cloud.vpc.handlers.NATStackHandler;
import org.hobbit.cloud.vpc.handlers.VPCStackHandler;
import org.hobbit.cloud.interfaces.ICloudClusterManager;
import org.hobbit.cloud.interfaces.Node;
import org.hobbit.cloud.interfaces.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author Pavel Smirnov. (psmirnov@agtinternational.com / smirnp@gmail.com)
 */
public class VpcClusterManager implements ICloudClusterManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(VpcClusterManager.class);

    protected AWSController awsController;

    protected AbstractStackHandler vpcStackHandler;
    protected AbstractStackHandler bastionStackHandler;
    protected AbstractStackHandler natStackHandler;
    private List<List<AbstractStackHandler>> stackList;
    protected long clusterCreated;



    public VpcClusterManager(String clusterName, String sshKeyName){
        awsController = new AWSController();

        try {
            awsController.init();
        }
        catch (Exception e){
            LOGGER.error("Failed to init aws controller: {}", e.getLocalizedMessage());
        }


        VPCStackHandler.Builder vpcHandlerBuilder = new VPCStackHandler.Builder<>()
                .name(clusterName+"-vpc");

        BastionStackHandler.Builder bastionBuilder = new BastionStackHandler.Builder<>()
                .name(clusterName+"-bastion")
                .vpcStackName(clusterName+"-vpc")
                .sshKeyName(sshKeyName);

        NATStackHandler.Builder natBuilder = new NATStackHandler.Builder<>()
                .name(clusterName+"-nat")
                .vpcStackName(clusterName+"-vpc")
                .sshKeyName(sshKeyName);

        vpcStackHandler = new VPCStackHandler(vpcHandlerBuilder);
        bastionStackHandler = new BastionStackHandler(bastionBuilder);
        natStackHandler = new NATStackHandler(natBuilder);

        stackList = new ArrayList<List<AbstractStackHandler>>() {{
            add(Arrays.asList(new AbstractStackHandler[]{vpcStackHandler}));
            add(Arrays.asList(new AbstractStackHandler[]{bastionStackHandler, natStackHandler}));
        }};
    }



    @Override
    public Resource getVPC() throws Exception{
        List<StackResourceSummary> summary = awsController.getStackResources(vpcStackHandler.getName(), "AWS::EC2::VPC");
        if(summary.isEmpty())
            return null;
        String id = summary.get(0).getPhysicalResourceId();
        return new Resource(id);
    }

    @Override
    public Node getBastion()  throws Exception{
        List<Node> nodes = getNodesFromAutoscalingGroup(bastionStackHandler.getName());
        if(nodes.isEmpty())
            return null;
        return nodes.get(0);
    }

    @Override
    public Node getNAT() throws Exception{
        List<Node> nodes = getNodesFromAutoscalingGroup(natStackHandler.getName());
        if(nodes.isEmpty())
            return null;
        return nodes.get(0);
    }

    protected List<Node> getNodesFromAutoscalingGroup(String stackName) throws Exception{
        List<Node> ret = new ArrayList<>();
        List<StackResourceSummary> asgResources = new ArrayList<>();
        try {
            asgResources = awsController.getStackResources(stackName, "AWS::AutoScaling::AutoScalingGroup");
            clusterCreated = 1;
        }
        catch (com.amazonaws.services.kms.model.NotFoundException e){
            //LOGGER.trace("Could not get {} stack resources {}", stackName, e.getLocalizedMessage());
        }

        if(asgResources.isEmpty())
            return ret;

        String asgName = asgResources.get(0).getPhysicalResourceId();
        List<Instance>  instances = awsController.getEC2InstancesByAutoscalingGroupName(asgName);
        for(Instance instance: instances){
            Node node = new Node(instance.getInstanceId())
                    .setPublicIpAddress(instance.getPublicIpAddress())
                    .setPrivateIpAddress(instance.getPrivateIpAddress());
            ret.add(node);
        }

        return ret;
    }

    @Override
    public void createCluster() throws Exception {
        createCluster(null);
    }

    @Override
    public void createCluster(String desiredConfiguration) throws Exception {
        awsController.createStacks(stackList);
    }



    @Override
    public void deleteCluster(String configuration) throws Exception {
        awsController.deleteStacks(stackList);
    }


    @Override
    public void deleteCluster() throws Exception{
        deleteCluster(null);
    }

}