package com.example.chordlab;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    DatabaseHelper myDb;
    EditText etUser, etPass;
    Button btnSignIn;
    SessionManager session;
    ImageView ivTogglePassword;
    boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        session = new SessionManager(this);

        // 1. Auto-login check: Go straight to Dashboard if already logged in
        if (session.isLoggedIn()) {
            goToDashboard();
            return;
        }

        TextView tvSignUp = findViewById(R.id.tvSignUpLink);
        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegistrationActivity.class)));

        myDb      = new DatabaseHelper(this);
        etUser    = findViewById(R.id.et_login_username);
        etPass    = findViewById(R.id.et_login_password);
        btnSignIn = findViewById(R.id.btnSignIn);

        btnSignIn.setOnClickListener(v -> {
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (myDb.checkUser(user, pass)) {
                // Save session
                session.saveSession(user, "");

                // Save username to UserSession for app-wide use
                SharedPreferences userPrefs = getSharedPreferences("UserSession", MODE_PRIVATE);
                userPrefs.edit().putString("username", user).apply();

                // 2. Restore saved details to the current session (Optional but helpful)
                SharedPreferences detailsPrefs = getSharedPreferences("DetailsPrefs", MODE_PRIVATE);
                String instrument = detailsPrefs.getString(user + "_instrument", "Guitar");
                String dailyGoal  = detailsPrefs.getString(user + "_dailyGoal",  "20 mins");

                userPrefs.edit()
                        .putString("instrument", instrument)
                        .putString("dailyGoal",  dailyGoal)
                        .apply();

                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();

                // 3. Go straight to Dashboard
                goToDashboard();

            } else {
                Toast.makeText(this, "Invalid Username or Password", Toast.LENGTH_SHORT).show();
            }
        });

        ivTogglePassword = findViewById(R.id.iv_toggle_password);
        ivTogglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;

            if (isPasswordVisible) {
                // 1. Show Password: Use HideReturnsTransformationMethod
                etPass.setTransformationMethod(android.text.method.HideReturnsTransformationMethod.getInstance());
                ivTogglePassword.setImageResource(R.drawable.ic_visibility_off);
            } else {
                // 2. Hide Password: Use PasswordTransformationMethod
                etPass.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
                ivTogglePassword.setImageResource(R.drawable.ic_visibility_on);
            }

            // 3. CRITICAL: Move cursor to the end so it doesn't jump to the start
            if (etPass.getText() != null) {
                etPass.setSelection(etPass.getText().length());
            }
        });
    }

    private void goToDashboard() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        // This ensures the user can't press 'Back' to return to the Login screen
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}