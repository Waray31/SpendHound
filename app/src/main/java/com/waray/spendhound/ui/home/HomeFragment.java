package com.waray.spendhound.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.waray.spendhound.AddTransactionActivity;
import com.waray.spendhound.DeclareDatabase;
import com.waray.spendhound.LoginActivity;
import com.waray.spendhound.MainActivity;
import com.waray.spendhound.R;
import com.waray.spendhound.databinding.FragmentHomeBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private TextView day7TextView, day6TextView, day5TextView, day4TextView, day3TextView, day2TextView, day1TextView;
    private FragmentHomeBinding binding;
    private ImageView profileImageView;
    private FloatingActionButton fab_addTransaction;
    private CardView cardViewProfile;
    public FirebaseAuth mAuth;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        profileImageView = view.findViewById(R.id.profileImageView);
        day7TextView = view.findViewById(R.id.day7);
        day6TextView = view.findViewById(R.id.day6);
        day5TextView = view.findViewById(R.id.day5);
        day4TextView = view.findViewById(R.id.day4);
        day3TextView = view.findViewById(R.id.day3);
        day2TextView = view.findViewById(R.id.day2);
        day1TextView = view.findViewById(R.id.day1);
        fab_addTransaction = view.findViewById(R.id.fab_addTransaction);
        cardViewProfile = view.findViewById(R.id.cardView_profile);
        mAuth = DeclareDatabase.getAuth();

        LogoutButton();
        addTransactionButton();
        setTextViews();
        setProfileImage(profileImageView);

        // Get the hosting Activity and remove the ActionBar
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null && activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().hide();
        }

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        callMainActivityMethod();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void callMainActivityMethod() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            mainActivity.getRecentTransaction();
            mainActivity.getTotalMonthSpends();
            mainActivity.getEverydaySpends();
        }
    }

    public void setTextViews() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -6); // Start with 6 days ago

        // Set the text for each TextView
        day7TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day6TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day5TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day4TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day3TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day2TextView.setText(getFormattedDay(calendar));
        calendar.add(Calendar.DAY_OF_YEAR, 1);
        day1TextView.setText(getFormattedDay(calendar));
    }

    public String getFormattedDay(Calendar calendar) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        return dateFormat.format(calendar.getTime());
    }

    public void setProfileImage(ImageView imageView) {
        // Check if the fragment is attached to an activity
        if (isAdded()) {
            String userId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
            StorageReference storageRef = FirebaseStorage.getInstance().getReference("profile_images").child(userId);
            storageRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                @Override
                public void onSuccess(Uri downloadUri) {
                    // Set the retrieved image to the provided ImageView
                    Glide.with(requireContext()).load(downloadUri).into(imageView);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    // Handle image retrieval failure
                    imageView.setImageResource(R.drawable.placeholder_profile_image);
                }
            });
        } else {
            // Handle the case when the fragment is not attached to an activity
        }
    }



    public void addTransactionButton(){
        fab_addTransaction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to navigate to NewActivity
                Intent intent = new Intent(getActivity(), AddTransactionActivity.class);
                startActivity(intent);
            }
        });
    }

    public void LogoutButton(){
        cardViewProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popupMenu = new PopupMenu(getActivity(), cardViewProfile, Gravity.END, androidx.transition.R.attr.popupMenuStyle, 0);
                popupMenu.inflate(R.menu.dropdown_menu);

                // Set a click listener for menu items
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.menu_logout) {
                            // Handle logout action
                            Toast.makeText(getActivity(), "Logout Successfully", Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                            Intent intent = new Intent(getActivity(), LoginActivity.class);
                            startActivity(intent);
                            return true;
                        } else {
                            // Handle other menu item clicks
                            return false;
                        }
                    }
                });

                popupMenu.show();
            }
        });
    }

}
