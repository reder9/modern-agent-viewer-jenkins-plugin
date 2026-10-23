package io.jenkins.plugins.fleetlens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Node;
import hudson.model.Slave;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.fleetlens.api.dto.ProvisionerDto;
import io.jenkins.plugins.fleetlens.detector.AgentProvisionerDetector;
import io.jenkins.plugins.fleetlens.detector.AwsEc2Detector;
import io.jenkins.plugins.fleetlens.detector.AzureDetector;
import io.jenkins.plugins.fleetlens.detector.BuiltInNodeDetector;
import io.jenkins.plugins.fleetlens.detector.DockerDetector;
import io.jenkins.plugins.fleetlens.detector.GcpDetector;
import io.jenkins.plugins.fleetlens.detector.KubernetesDetector;
import io.jenkins.plugins.fleetlens.detector.NomadDetector;
import io.jenkins.plugins.fleetlens.detector.OpenStackDetector;
import io.jenkins.plugins.fleetlens.detector.ProxmoxDetector;
import io.jenkins.plugins.fleetlens.detector.StaticNodeDetector;
import io.jenkins.plugins.fleetlens.detector.SwarmDetector;
import io.jenkins.plugins.fleetlens.detector.VSphereDetector;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AgentProvisionerDetectorTest {

    @Test
    void testBuiltInControllerDetector(JenkinsRule j) {
        BuiltInNodeDetector detector = new BuiltInNodeDetector();
        assertTrue(detector.isApplicable(j.jenkins.toComputer(), j.jenkins));
        ProvisionerDto dto = detector.detect(j.jenkins.toComputer(), j.jenkins);
        assertNotNull(dto);
        assertEquals("controller", dto.getId());
        assertEquals("Controller (Built-in)", dto.getDisplayName());
    }

    @Test
    void testStaticAgentDetector(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createOnlineSlave();
        StaticNodeDetector detector = new StaticNodeDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertNotNull(dto);
        assertEquals("static", dto.getId());
    }

    @Test
    void testDockerDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Agent running in Docker container");
        DockerDetector detector = new DockerDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("docker", dto.getId());
    }

    @Test
    void testKubernetesDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Kubernetes dynamic agent pod");
        KubernetesDetector detector = new KubernetesDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("kubernetes", dto.getId());
    }

    @Test
    void testAwsEc2DetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Provisioned on Amazon EC2 spot instance");
        AwsEc2Detector detector = new AwsEc2Detector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("aws-ec2", dto.getId());
    }

    @Test
    void testAzureDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Azure dynamic VM agent");
        AzureDetector detector = new AzureDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("azure", dto.getId());
    }

    @Test
    void testGcpDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Google Cloud Engine GCE ephemeral worker");
        GcpDetector detector = new GcpDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("gcp", dto.getId());
    }

    @Test
    void testProxmoxDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Proxmox VE KVM virtual machine");
        ProxmoxDetector detector = new ProxmoxDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("proxmox", dto.getId());
        assertEquals("Proxmox VE", dto.getDisplayName());
    }

    @Test
    void testVSphereDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("VMware vSphere ESXi cluster agent");
        VSphereDetector detector = new VSphereDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("vsphere", dto.getId());
        assertEquals("VMware vSphere", dto.getDisplayName());
    }

    @Test
    void testSwarmDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("Auto-discovered swarm client agent");
        SwarmDetector detector = new SwarmDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("swarm", dto.getId());
        assertEquals("Jenkins Swarm", dto.getDisplayName());
    }

    @Test
    void testOpenStackDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("OpenStack Nova ephemeral instance");
        OpenStackDetector detector = new OpenStackDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("openstack", dto.getId());
        assertEquals("OpenStack", dto.getDisplayName());
    }

    @Test
    void testNomadDetectorApplicable(JenkinsRule j) throws Exception {
        DumbSlave slave = j.createSlave();
        slave.setNodeDescription("HashiCorp Nomad dynamic batch runner");
        NomadDetector detector = new NomadDetector();
        assertTrue(detector.isApplicable(slave.toComputer(), slave));
        ProvisionerDto dto = detector.detect(slave.toComputer(), slave);
        assertEquals("nomad", dto.getId());
        assertEquals("HashiCorp Nomad", dto.getDisplayName());
    }

    @Test
    void testGenericCloudDetectorSpecializedClouds(JenkinsRule j) throws Exception {
        io.jenkins.plugins.fleetlens.detector.GenericCloudDetector detector = new io.jenkins.plugins.fleetlens.detector.GenericCloudDetector();

        // 1. Oracle Cloud Infrastructure
        DumbSlave ociSlave = j.createSlave();
        ociSlave.setNodeDescription("Oracle Cloud Infrastructure OCI Compute Instance");
        assertTrue(detector.isApplicable(ociSlave.toComputer(), ociSlave));
        ProvisionerDto ociDto = detector.detect(ociSlave.toComputer(), ociSlave);
        assertEquals("oci", ociDto.getId());
        assertEquals("Oracle Cloud (OCI)", ociDto.getDisplayName());

        // 2. DigitalOcean
        DumbSlave doSlave = j.createSlave();
        doSlave.setNodeDescription("DigitalOcean Droplet s-2vcpu-4gb");
        assertTrue(detector.isApplicable(doSlave.toComputer(), doSlave));
        ProvisionerDto doDto = detector.detect(doSlave.toComputer(), doSlave);
        assertEquals("digitalocean", doDto.getId());
        assertEquals("DigitalOcean", doDto.getDisplayName());

        // 3. Hetzner Cloud
        DumbSlave hetznerSlave = j.createSlave();
        hetznerSlave.setNodeDescription("Hetzner Cloud CPX21 VM");
        assertTrue(detector.isApplicable(hetznerSlave.toComputer(), hetznerSlave));
        ProvisionerDto hetznerDto = detector.detect(hetznerSlave.toComputer(), hetznerSlave);
        assertEquals("hetzner", hetznerDto.getId());
        assertEquals("Hetzner Cloud", hetznerDto.getDisplayName());

        // 4. Amazon ECS / Fargate
        DumbSlave ecsSlave = j.createSlave();
        ecsSlave.setNodeDescription("Amazon ECS Fargate container task");
        assertTrue(detector.isApplicable(ecsSlave.toComputer(), ecsSlave));
        ProvisionerDto ecsDto = detector.detect(ecsSlave.toComputer(), ecsSlave);
        assertEquals("aws-ecs", ecsDto.getId());
        assertEquals("Amazon ECS / Fargate", ecsDto.getDisplayName());
    }
}
