package com.waray.spendhound;

import java.util.List;

public class PayerGroup {
    private String groupId;
    private String groupName;
    private List<String> members;           // Now stores UIDs (was usernames)
    private String createdBy;
    private List<String> memberDisplayNames; // Display names for UI

    public PayerGroup() {
        // Default constructor required for Firebase
    }

    public PayerGroup(String groupId, String groupName, List<String> members, String createdBy) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.members = members;
        this.createdBy = createdBy;
    }

    // New constructor with display names
    public PayerGroup(String groupId, String groupName, List<String> members, String createdBy, List<String> memberDisplayNames) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.members = members;
        this.createdBy = createdBy;
        this.memberDisplayNames = memberDisplayNames;
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

    public List<String> getMemberDisplayNames() {
        return memberDisplayNames;
    }

    public void setMemberDisplayNames(List<String> memberDisplayNames) {
        this.memberDisplayNames = memberDisplayNames;
    }
}
