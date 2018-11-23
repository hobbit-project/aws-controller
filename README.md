# aws-controller

Architecture for building resource managers for cloud resources.

Includes the [VPC Cluster Manager](https://github.com/hobbit-project/aws-controller/blob/master/src/main/java/org/hobbit/cloud/vpc/VpcClusterManager.java) as the simpliest example of cloud resources manager. It creates VPC, Bastion and NAT hosts using the [Widdix templates](https://github.com/widdix/aws-cf-templates/tree/master/vpc) (not part of the repo). 

Examples of other resource managers:
[Neptune Cluster Manager](https://github.com/hobbit-project/neptune-system-adapter/blob/master/src/main/java/org/hobbit/sparql_snb/systems/neptune/NeptuneClusterManager.java)
