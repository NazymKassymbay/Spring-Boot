package com.example.practicee1;

// Extension point: anything that wants to react to a greeting implements this
public interface GreetingListener {

    void onGreeting(String name);
}