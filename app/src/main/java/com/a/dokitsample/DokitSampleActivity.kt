package com.a.dokitsample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class DokitSampleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dokit_sample)
    }

    override fun onResume() {
        super.onResume()
        Thread.sleep(100)
        doSomethingA()
    }

    fun doSomethingA(){
        Thread.sleep(100)
        doSomethingB()
    }

    fun doSomethingB(){
        Thread.sleep(100)
        doSomethingC()
    }

    fun doSomethingC(){
        Thread.sleep(100)
        doSomethingD()
    }

    fun doSomethingD(){
        Thread.sleep(100)
        doSomethingE()
    }

    fun doSomethingE(){
        Thread.sleep(100)
    }

}