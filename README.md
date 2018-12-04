# aws-controller

Architecture for building resource managers for cloud resources.

## Features
- Clients to the following AWS Services: Cloud Formation, EC2, AutoScaling, S3, etc.
- Layered stacks execution
- SSH Client

## Manager examples:
- [VPC Cluster Manager](https://github.com/hobbit-project/aws-controller/blob/master/src/main/java/org/hobbit/cloud/vpc/VpcClusterManager.java) as the simpliest example of cloud resources manager. It creates VPC, Bastion and NAT hosts using the [Widdix templates](https://github.com/widdix/aws-cf-templates/tree/master/vpc) (not part of the repo). 

- [Swarm Cluster Manager](https://github.com/hobbit-project/platform/blob/cloud/platform-controller/src/main/java/org/hobbit/controller/cloud/aws/swarm/SwarmClusterManager.java) - part of the cloud-extended HOBBIT platfrom. Used for managing docker containers in AWS cloud.

- [Neptune Cluster Manager](https://github.com/hobbit-project/neptune-system-adapter/blob/master/src/main/java/org/hobbit/sparql_snb/systems/neptune/NeptuneClusterManager.java) - part of Neptune System Adapter compatible with the Data Storage Benchmark.
