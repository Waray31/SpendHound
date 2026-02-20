package com.waray.spendhound;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

public class AddTransactionActivity extends AppCompatActivity {

    private LinearLayout container;
    private LinearLayout groupsContainer;
    private Button btnAdd;
    private Button btnAddGroup;
    private Button addTransactionbtn;
    private Spinner payorSpinner;
    private Spinner transactionTypeSpinner;
    private String transactionType;
    public String paymentAmountStr;
    public String multilineStr;
    private ProgressBar progressBar;
    public List<String> usernames;
    public FirebaseAuth mAuth;
    private List<View> rows;
    private List<View> groupViews;
    private List<PayerGroup> payerGroups;
    public List<String> payorsList;
    public List<Integer> amountsPaidList;
    public Integer totalAmountPaid = 0;
    public Integer paymentAmount;
    private EditText paymentAmountEditText;
    private EditText editTextTextMultiLine;
    private TextView individualPayment;
    private int totalIndividualPayment, totalBalanced, totalUnpaid, totalOwed, totalDept;
    public String usernamePost;
    private String currentUserId;
    private ArrayList<RecentTransaction> recentTransactionList = new ArrayList<>();
    private PayerGroup selectedGroup = null;
    private View selectedGroupView = null;
    private PopupWindow payorTooltipPopup;
    private View firstRow = null;
    private List<String> groupMemberUsernames = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        // Get the Firebase Authentication instance
        mAuth = DeclareDatabase.getAuth();

        // Check the user's authentication state
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // User is not authenticated, you can redirect them to the login activity
            Intent intent = new Intent(AddTransactionActivity.this, LoginActivity.class);
            startActivity(intent);
            finish(); // Finish this activity to prevent returning to it when pressing back
            return;
        }

        transactionTypeSpinner = findViewById(R.id.transactionType);
        String[] transactionTypes = getResources().getStringArray(R.array.transactionTypes_String);
        SpinnerItem adapter = new SpinnerItem(this, Arrays.asList(transactionTypes));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        transactionTypeSpinner.setAdapter(adapter);

        container = findViewById(R.id.container);
        groupsContainer = findViewById(R.id.groupsContainer);
        btnAdd = findViewById(R.id.btnAdd);
        btnAddGroup = findViewById(R.id.btnAddGroup);
        rows = new ArrayList<>();
        groupViews = new ArrayList<>();
        payerGroups = new ArrayList<>();
        progressBar = findViewById(R.id.progressBar);
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        fetchUsernamesAndSetupInitialRow();
        loadExistingGroups();

        // Initially disable btnAdd and show tooltip since no group is selected
        btnAdd.setEnabled(false);
        btnAdd.setAlpha(0.5f);
        showPayorTooltip();

        btnAdd.setOnClickListener(v -> {
            if (selectedGroup == null) {
                // Show tooltip if no group is selected
                showPayorTooltip();
                return;
            }
            if (groupMemberUsernames != null && rows.size() < groupMemberUsernames.size() - 1) {
                addRow();
            } else {
                Toast.makeText(AddTransactionActivity.this, "You can't add more payors.", Toast.LENGTH_SHORT).show();
            }
        });

        addTransactionbtn = findViewById(R.id.addTransactionbtn);
        addTransactionbtn.setOnClickListener(v -> addTransaction());

        btnAddGroup.setOnClickListener(v -> showCreateGroupDialog());

        paymentAmountEditText = findViewById(R.id.paymentAmount);
        individualPayment = findViewById(R.id.individualPayment);
        setupIndividualPaymentCalculator();

        detailsCharacterCount();
        exitEditText();
    }

    private void fetchUsernamesAndSetupInitialRow() {
        DatabaseReference databaseReference = DeclareDatabase.getDatabaseReference();
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                usernames = new ArrayList<>();
                usernames.add("Select a payor:");
                for (DataSnapshot userSnapshot : dataSnapshot.getChildren()) {
                    String username = userSnapshot.child("username").getValue(String.class);
                    if (username != null) {
                        usernames.add(username);
                    }
                }
                // Add the initial row but hide it (no group selected initially)
                addRow();
                if (rows.size() > 0) {
                    firstRow = rows.get(0);
                    firstRow.setVisibility(View.GONE);
                }
                // Keep btnAdd disabled until a group is selected
                btnAdd.setEnabled(false);
                btnAdd.setAlpha(0.5f);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
                Toast.makeText(getApplicationContext(), "Failed to load users.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void addRow() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View row = inflater.inflate(R.layout.row_layout, container, false);

        payorSpinner = row.findViewById(R.id.payor);

        // Use group members if a group is selected, otherwise use all usernames
        List<String> spinnerUsernames = (groupMemberUsernames != null && !groupMemberUsernames.isEmpty())
                ? groupMemberUsernames
                : usernames;

        if (spinnerUsernames != null && !spinnerUsernames.isEmpty()) {
            SpinnerItem adapter = new SpinnerItem(AddTransactionActivity.this, spinnerUsernames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            payorSpinner.setAdapter(adapter);
        }

        Button btnMinus = row.findViewById(R.id.closeBtn);
        btnMinus.setOnClickListener(v -> removeRow(row));

        Drawable roundedDrawable = getResources().getDrawable(R.drawable.rounded_alternating_row);
        ViewCompat.setBackground(row, roundedDrawable);

        rows.add(row);
        container.addView(row);
    }

    private void removeRow(View row) {
        container.removeView(row);
        rows.remove(row);
    }

    private void addTransaction() {
        progressBar.setVisibility(View.VISIBLE);
        transactionType = transactionTypeSpinner.getSelectedItem().toString();
        paymentAmountEditText = findViewById(R.id.paymentAmount);
        editTextTextMultiLine = findViewById(R.id.editTextTextMultiLine);

        paymentAmountStr = paymentAmountEditText.getText().toString();
        multilineStr = editTextTextMultiLine.getText().toString();

        if (TextUtils.isEmpty(paymentAmountStr)) {
            paymentAmount = 0;
        } else {
            paymentAmount = Integer.parseInt(paymentAmountStr);
        }

        payorsList = new ArrayList<>();
        amountsPaidList = new ArrayList<>();

        if ("Select a transaction:".equals(transactionType) || paymentAmount == 0) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }

        // Check if a group is selected
        if (selectedGroup != null && selectedGroup.getMembers() != null && !selectedGroup.getMembers().isEmpty()) {
            // Use the selected group's members as payors with equal individual payment
            List<String> groupMembers = selectedGroup.getMembers();
            int numberOfMembers = groupMembers.size();
            int individualAmount = paymentAmount / numberOfMembers;
            int remainder = paymentAmount % numberOfMembers;

            totalAmountPaid = 0;
            for (int i = 0; i < groupMembers.size(); i++) {
                payorsList.add(groupMembers.get(i));
                // Distribute remainder to first few members to ensure total matches
                int amount = individualAmount + (i < remainder ? 1 : 0);
                amountsPaidList.add(amount);
                totalAmountPaid += amount;
            }

            totalIndividualPayment = individualAmount;
        } else {
            // Fall back to manual payors from rows if no group is selected
            HashSet<String> uniquePayors = new HashSet<>();
            totalAmountPaid = 0;

            for (View row : rows) {
                Spinner payorSpinner = row.findViewById(R.id.payor);
                EditText amountPaidEditText = row.findViewById(R.id.amountPaid);

                String payor = payorSpinner.getSelectedItem().toString();
                String amountPaidStr = amountPaidEditText.getText().toString().trim();

                if ("Select a payor:".equals(payor) || TextUtils.isEmpty(amountPaidStr)) {
                    Toast.makeText(AddTransactionActivity.this, "Please select a group or fill in all payor fields", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                if (!uniquePayors.add(payor)) {
                    Toast.makeText(AddTransactionActivity.this, "Duplicate payor detected: " + payor, Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                try {
                    int amountPaid = Integer.parseInt(amountPaidStr);
                    payorsList.add(payor);
                    amountsPaidList.add(amountPaid);
                    totalAmountPaid += amountPaid;
                } catch (NumberFormatException e) {
                    Toast.makeText(AddTransactionActivity.this, "Invalid amount format for payor: " + payor, Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    return;
                }
            }

            if (!paymentAmount.equals(totalAmountPaid)) {
                Toast.makeText(AddTransactionActivity.this, "Total amount paid does not match the payment amount.", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
                return;
            }
        }

        String currentUserID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference usersRef = DeclareDatabase.getDatabaseReference().child(currentUserID);

        usersRef.child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    usernamePost = dataSnapshot.getValue(String.class);
                    saveTransaction();
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Username not found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                progressBar.setVisibility(View.GONE);
                Log.e("FirebaseDatabase", "Database read error occurred: " + databaseError.getMessage());
            }
        });
    }

    private void saveTransaction() {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMMM-yyyy", Locale.getDefault());
        SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        String currentMonthYear = dateFormat.format(calendar.getTime());
        String currentDay = dayFormat.format(calendar.getTime());
        String currentTime = timeFormat.format(calendar.getTime());

        DatabaseReference timestampRef = DeclareDatabase.getDBRefTransaction()
                .child(currentMonthYear)
                .child(currentDay)
                .child(currentTime);

        Transaction transaction;
        if (selectedGroup != null) {
            transaction = new Transaction(transactionType, paymentAmount.intValue(), multilineStr, payorsList, amountsPaidList, usernamePost, totalIndividualPayment, selectedGroup.getGroupId(), selectedGroup.getGroupName());
        } else {
            transaction = new Transaction(transactionType, paymentAmount.intValue(), multilineStr, payorsList, amountsPaidList, usernamePost, totalIndividualPayment);
        }

        timestampRef.setValue(transaction)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Transaction added successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(AddTransactionActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AddTransactionActivity.this, "Failed to add transaction", Toast.LENGTH_SHORT).show();
                });
    }


    private void setupIndividualPaymentCalculator() {
        paymentAmountEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                calculateAndDisplayIndividualPayment();
            }
        });
    }

    private void calculateAndDisplayIndividualPayment() {
        String amountStr = paymentAmountEditText.getText().toString();
        if (!TextUtils.isEmpty(amountStr)) {
            try {
                int amount = Integer.parseInt(amountStr);
                int numberOfUsers;

                // If a group is selected, use the group's member count
                if (selectedGroup != null && selectedGroup.getMembers() != null && !selectedGroup.getMembers().isEmpty()) {
                    numberOfUsers = selectedGroup.getMembers().size();
                } else if (usernames != null && usernames.size() > 1) {
                    // Fall back to all users if no group is selected
                    numberOfUsers = usernames.size() - 1; // Exclude "Select a payor:"
                } else {
                    individualPayment.setText("₱ 0.00");
                    return;
                }

                totalIndividualPayment = amount / numberOfUsers;
                individualPayment.setText("₱ " + totalIndividualPayment + ".00");
            } catch (NumberFormatException e) {
                individualPayment.setText("₱ 0.00");
            }
        } else {
            individualPayment.setText("₱ 0.00");
        }
    }

    private void detailsCharacterCount() {
        // Implement character count logic here if needed
    }

    private void exitEditText() {
        // Implement exit edit text logic here if needed
    }

    private void loadExistingGroups() {
        DatabaseReference groupsRef = DeclareDatabase.getDBRefGroups().child(currentUserId);
        groupsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                groupsContainer.removeAllViews();
                groupViews.clear();
                payerGroups.clear();

                for (DataSnapshot groupSnapshot : dataSnapshot.getChildren()) {
                    PayerGroup group = groupSnapshot.getValue(PayerGroup.class);
                    if (group != null) {
                        group.setGroupId(groupSnapshot.getKey());
                        payerGroups.add(group);
                        addGroupView(group);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("FirebaseDatabase", "Failed to load groups: " + databaseError.getMessage());
            }
        });
    }

    private void addGroupView(PayerGroup group) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View groupView = inflater.inflate(R.layout.item_group, groupsContainer, false);

        TextView groupNameTV = groupView.findViewById(R.id.groupName);
        TextView groupMembersTV = groupView.findViewById(R.id.groupMembers);
        Button editBtn = groupView.findViewById(R.id.editGroupBtn);
        Button removeBtn = groupView.findViewById(R.id.removeGroupBtn);

        groupNameTV.setText(group.getGroupName());
        String membersText = "Members: " + String.join(", ", group.getMembers());
        groupMembersTV.setText(membersText);

        editBtn.setOnClickListener(v -> showEditGroupDialog(group, groupView));
        removeBtn.setOnClickListener(v -> showRemoveGroupConfirmation(group, groupView));

        // Add click listener for group selection
        groupView.setOnClickListener(v -> selectGroup(group, groupView));

        groupViews.add(groupView);
        groupsContainer.addView(groupView);
    }

    private void selectGroup(PayerGroup group, View groupView) {
        // If the same group is tapped again, deselect it
        if (selectedGroup != null && selectedGroup.getGroupId().equals(group.getGroupId())) {
            deselectGroup();
            return;
        }

        // Deselect previous group if any
        if (selectedGroupView != null) {
            selectedGroupView.setBackgroundResource(R.drawable.rounded_border_transparent_bg);
        }

        // Select the new group
        selectedGroup = group;
        selectedGroupView = groupView;
        groupView.setBackgroundResource(R.drawable.rounded_border_selected_bg);

        // Set group member usernames for the spinner
        groupMemberUsernames = new ArrayList<>();
        groupMemberUsernames.add("Select a payor:");
        groupMemberUsernames.addAll(group.getMembers());

        // Enable btnAdd and hide tooltip when a group is selected
        btnAdd.setEnabled(true);
        btnAdd.setAlpha(1.0f);
        dismissPayorTooltip();

        // Show and update the first row with group members
        if (firstRow != null) {
            firstRow.setVisibility(View.VISIBLE);
            updateRowSpinnerWithGroupMembers(firstRow);
        }

        // Update existing rows' spinners with group members
        for (View row : rows) {
            updateRowSpinnerWithGroupMembers(row);
        }

        // Recalculate individual payment based on selected group members
        calculateAndDisplayIndividualPayment();

        Toast.makeText(this, "Selected group: " + group.getGroupName(), Toast.LENGTH_SHORT).show();
    }

    private void updateRowSpinnerWithGroupMembers(View row) {
        Spinner spinner = row.findViewById(R.id.payor);
        if (spinner != null && groupMemberUsernames != null && !groupMemberUsernames.isEmpty()) {
            SpinnerItem adapter = new SpinnerItem(AddTransactionActivity.this, groupMemberUsernames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    private void deselectGroup() {
        if (selectedGroupView != null) {
            selectedGroupView.setBackgroundResource(R.drawable.rounded_border_transparent_bg);
        }
        selectedGroup = null;
        selectedGroupView = null;
        groupMemberUsernames = null;

        // Disable btnAdd and show tooltip when no group is selected
        btnAdd.setEnabled(false);
        btnAdd.setAlpha(0.5f);
        showPayorTooltip();

        // Hide the first row when no group is selected
        if (firstRow != null) {
            firstRow.setVisibility(View.GONE);
        }

        // Remove all additional rows (keep only the first hidden row)
        while (rows.size() > 1) {
            View row = rows.get(rows.size() - 1);
            container.removeView(row);
            rows.remove(row);
        }

        calculateAndDisplayIndividualPayment();
        Toast.makeText(this, "Group deselected", Toast.LENGTH_SHORT).show();
    }

    private void showCreateGroupDialog() {
        if (usernames == null || usernames.size() <= 1) {
            Toast.makeText(this, "No users available to add to group", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_group, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText groupNameEditText = dialogView.findViewById(R.id.groupNameEditText);
        LinearLayout usersCheckboxContainer = dialogView.findViewById(R.id.usersCheckboxContainer);
        Button cancelBtn = dialogView.findViewById(R.id.cancelGroupBtn);
        Button createBtn = dialogView.findViewById(R.id.createGroupBtn);

        List<CheckBox> checkBoxes = new ArrayList<>();

        // Add checkboxes for each user (skip "Select a payor:")
        for (int i = 1; i < usernames.size(); i++) {
            String username = usernames.get(i);
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(username);
            checkBox.setTextColor(getResources().getColor(R.color.darkBlue));
            checkBox.setPadding(8, 8, 8, 8);
            checkBoxes.add(checkBox);
            usersCheckboxContainer.addView(checkBox);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        createBtn.setOnClickListener(v -> {
            String groupName = groupNameEditText.getText().toString().trim();

            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> selectedMembers = new ArrayList<>();
            for (CheckBox checkBox : checkBoxes) {
                if (checkBox.isChecked()) {
                    selectedMembers.add(checkBox.getText().toString());
                }
            }

            if (selectedMembers.isEmpty()) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show();
                return;
            }

            saveGroupToDatabase(groupName, selectedMembers);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveGroupToDatabase(String groupName, List<String> members) {
        DatabaseReference groupsRef = DeclareDatabase.getDBRefGroups().child(currentUserId);
        String groupId = groupsRef.push().getKey();

        if (groupId != null) {
            PayerGroup newGroup = new PayerGroup(groupId, groupName, members, currentUserId);
            groupsRef.child(groupId).setValue(newGroup)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Group created successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Failed to create group", Toast.LENGTH_SHORT).show();
                        Log.e("FirebaseDatabase", "Failed to save group: " + e.getMessage());
                    });
        }
    }

    private void showEditGroupDialog(PayerGroup group, View groupView) {
        if (usernames == null || usernames.size() <= 1) {
            Toast.makeText(this, "No users available to edit group", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_group, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText groupNameEditText = dialogView.findViewById(R.id.editGroupNameEditText);
        LinearLayout usersCheckboxContainer = dialogView.findViewById(R.id.editUsersCheckboxContainer);
        Button cancelBtn = dialogView.findViewById(R.id.cancelEditGroupBtn);
        Button saveBtn = dialogView.findViewById(R.id.saveEditGroupBtn);

        // Pre-fill the group name
        groupNameEditText.setText(group.getGroupName());

        List<CheckBox> checkBoxes = new ArrayList<>();

        // Add checkboxes for each user (skip "Select a payor:")
        for (int i = 1; i < usernames.size(); i++) {
            String username = usernames.get(i);
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(username);
            checkBox.setTextColor(getResources().getColor(R.color.darkBlue));
            checkBox.setPadding(8, 8, 8, 8);

            // Check if this user is already in the group
            if (group.getMembers().contains(username)) {
                checkBox.setChecked(true);
            }

            checkBoxes.add(checkBox);
            usersCheckboxContainer.addView(checkBox);
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        saveBtn.setOnClickListener(v -> {
            String groupName = groupNameEditText.getText().toString().trim();

            if (TextUtils.isEmpty(groupName)) {
                Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> selectedMembers = new ArrayList<>();
            for (CheckBox checkBox : checkBoxes) {
                if (checkBox.isChecked()) {
                    selectedMembers.add(checkBox.getText().toString());
                }
            }

            if (selectedMembers.isEmpty()) {
                Toast.makeText(this, "Please select at least one member", Toast.LENGTH_SHORT).show();
                return;
            }

            updateGroupInDatabase(group.getGroupId(), groupName, selectedMembers, groupView);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateGroupInDatabase(String groupId, String groupName, List<String> members, View groupView) {
        DatabaseReference groupRef = DeclareDatabase.getDBRefGroups()
                .child(currentUserId)
                .child(groupId);

        PayerGroup updatedGroup = new PayerGroup(groupId, groupName, members, currentUserId);
        groupRef.setValue(updatedGroup)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Group updated successfully", Toast.LENGTH_SHORT).show();

                    // Update the group view immediately
                    TextView groupNameTV = groupView.findViewById(R.id.groupName);
                    TextView groupMembersTV = groupView.findViewById(R.id.groupMembers);
                    groupNameTV.setText(groupName);
                    String membersText = "Members: " + String.join(", ", members);
                    groupMembersTV.setText(membersText);

                    // If this group is currently selected, update the selected group object
                    if (selectedGroup != null && selectedGroup.getGroupId().equals(groupId)) {
                        selectedGroup.setGroupName(groupName);
                        selectedGroup.setMembers(members);
                        calculateAndDisplayIndividualPayment();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update group", Toast.LENGTH_SHORT).show();
                    Log.e("FirebaseDatabase", "Failed to update group: " + e.getMessage());
                });
    }

    private void showRemoveGroupConfirmation(PayerGroup group, View groupView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_group, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView groupNameTV = dialogView.findViewById(R.id.groupNameToRemove);
        Button cancelBtn = dialogView.findViewById(R.id.cancelRemoveBtn);
        Button confirmBtn = dialogView.findViewById(R.id.confirmRemoveBtn);

        groupNameTV.setText(group.getGroupName());

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        confirmBtn.setOnClickListener(v -> {
            removeGroupFromDatabase(group, groupView);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void removeGroupFromDatabase(PayerGroup group, View groupView) {
        DatabaseReference groupRef = DeclareDatabase.getDBRefGroups()
                .child(currentUserId)
                .child(group.getGroupId());

        groupRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Group removed successfully", Toast.LENGTH_SHORT).show();
                    groupsContainer.removeView(groupView);
                    groupViews.remove(groupView);
                    payerGroups.remove(group);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to remove group", Toast.LENGTH_SHORT).show();
                    Log.e("FirebaseDatabase", "Failed to remove group: " + e.getMessage());
                });
    }

    private void showPayorTooltip() {
        if (btnAdd == null) return;

        // Dismiss existing tooltip if any
        dismissPayorTooltip();

        // Inflate tooltip view from XML
        View tooltipView = LayoutInflater.from(this).inflate(R.layout.tooltip_add_payor, null);

        // Create PopupWindow
        payorTooltipPopup = new PopupWindow(
                tooltipView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
        );
        payorTooltipPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        payorTooltipPopup.setOutsideTouchable(false);

        // Measure tooltip to get its dimensions
        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int tooltipHeight = tooltipView.getMeasuredHeight();
        int tooltipWidth = tooltipView.getMeasuredWidth();

        // Post to ensure the button is laid out before showing tooltip
        btnAdd.post(() -> {
            if (payorTooltipPopup != null && btnAdd.isAttachedToWindow()) {
                // Position tooltip at upper left corner of the button
                // offsetX = negative tooltip width to position left of the button's left edge
                // offsetY = negative (button height + tooltip height) to position above the button
                int offsetX = -tooltipWidth;
                int offsetY = -(btnAdd.getHeight() + tooltipHeight);
                payorTooltipPopup.showAsDropDown(btnAdd, offsetX, offsetY, Gravity.START);
            }
        });
    }

    private void dismissPayorTooltip() {
        if (payorTooltipPopup != null && payorTooltipPopup.isShowing()) {
            payorTooltipPopup.dismiss();
            payorTooltipPopup = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissPayorTooltip();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                int[] location = new int[2];
                v.getLocationOnScreen(location);
                float x = event.getRawX();
                float y = event.getRawY();

                // Check if touch is outside the focused EditText
                if (x < location[0] || x > location[0] + v.getWidth() ||
                    y < location[1] || y > location[1] + v.getHeight()) {
                    hideKeyboard(v);
                    v.clearFocus();
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}