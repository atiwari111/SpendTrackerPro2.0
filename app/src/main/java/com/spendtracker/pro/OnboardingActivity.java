package com.spendtracker.pro;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

public class OnboardingActivity extends AppCompatActivity {

    ViewPager2 viewPager;
    Button btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        btnNext   = findViewById(R.id.btnNext);

        OnboardingAdapter adapter = new OnboardingAdapter();
        viewPager.setAdapter(adapter);

        // Connect dots indicator to ViewPager2 so it tracks the current page
        com.tbuonomo.viewpagerdotsindicator.DotsIndicator dotsIndicator =
                findViewById(R.id.dots_indicator);
        if (dotsIndicator != null) {
            dotsIndicator.attachTo(viewPager);
        }

        btnNext.setOnClickListener(v -> {

            if(viewPager.getCurrentItem() < 2){
                viewPager.setCurrentItem(viewPager.getCurrentItem()+1);
            }else{

                SharedPreferences pref = getSharedPreferences("app",MODE_PRIVATE);
                pref.edit().putBoolean("firstLaunch",false).apply();

                startActivity(new Intent(this, MainActivity.class));
                finish();
            }
        });
    }
}
