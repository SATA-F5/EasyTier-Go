package com.easytier.fabric.lan.config;

import java.util.ArrayList;
import java.util.List;

/**
 * EasyTier TOML 配置模型
 * 对应 config.toml 的完整字段
 */
public class TomlConfig {

    private String instanceName;
    private String instanceId;
    private boolean dhcp = true;
    private String manualIpv4;
    private List<String> listeners = new ArrayList<>();
    private String networkName;
    private String networkSecret = "";
    private String relayServer;
    private String devName = "et_fabric";
    private String generatedTimestamp;
    private boolean corrupted = false;
    private String sourceFileName;

    public TomlConfig() {
        this.instanceId = generateInstanceId();
        this.listeners.add("tcp://0.0.0.0:11010");
        this.listeners.add("udp://0.0.0.0:11010");
        this.listeners.add("wg://0.0.0.0:11011");
    }

    /**
     * 生成 32 位 UUID（无连字符）作为 instance_id
     * EasyTier 要求 instance_id 为 UUID simple format（32 位十六进制）
     */
    public static String generateInstanceId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public boolean isDhcp() {
        return dhcp;
    }

    public void setDhcp(boolean dhcp) {
        this.dhcp = dhcp;
    }

    public String getManualIpv4() {
        return manualIpv4;
    }

    public void setManualIpv4(String manualIpv4) {
        this.manualIpv4 = manualIpv4;
    }

    public List<String> getListeners() {
        return listeners;
    }

    public void setListeners(List<String> listeners) {
        this.listeners = listeners != null ? listeners : new ArrayList<>();
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    public String getNetworkSecret() {
        return networkSecret;
    }

    public void setNetworkSecret(String networkSecret) {
        this.networkSecret = networkSecret != null ? networkSecret : "";
    }

    public String getRelayServer() {
        return relayServer;
    }

    public void setRelayServer(String relayServer) {
        this.relayServer = relayServer;
    }

    public String getDevName() {
        return devName;
    }

    public void setDevName(String devName) {
        this.devName = devName != null && !devName.isEmpty() ? devName : "et_fabric";
    }

    public String getGeneratedTimestamp() {
        return generatedTimestamp;
    }

    public void setGeneratedTimestamp(String generatedTimestamp) {
        this.generatedTimestamp = generatedTimestamp;
    }

    public boolean isCorrupted() {
        return corrupted;
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    /**
     * 计算磁盘文件名: <sanitize(network_name)>.toml
     */
    public String toFileName() {
        return FileSanitizer.sanitize(networkName) + ".toml";
    }
}
