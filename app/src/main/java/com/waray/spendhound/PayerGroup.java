package com.waray.spendhound;

import java.util.List;

public class PayerGroup {
    private String groupId;
    private String groupName;
    private List<String> members;
    private String createdBy;

    public PayerGroup() {
        // Default constructor required for Firebase
    }

    public PayerGroup(String groupId, String groupName, List<String> members, String createdBy) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.members = members;
        this.createdBy = createdBy;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
