package com.piotrprus.testconcurrency

import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.random.Random

class MainViewModel : ViewModel() {

    val tasksInProgress = MediatorLiveData<Boolean>()
    private val taskARunning = MutableLiveData<Boolean>()
    private val taskBRunning = MutableLiveData<Boolean>()
    private var taskACounter = 0
    private var taskBCounter = 0
    private var customExecutor =
        Executors.newFixedThreadPool(1).asCoroutineDispatcher()
    private val supervisorA = SupervisorJob()
    private val supervisorB = SupervisorJob()

    init {
        setupMediator()
    }

    private fun executeA(variable: Int) {
        viewModelScope.launch(context = Job(supervisorA).plus(customExecutor)) {
            taskA(variable)
        }
    }

    private fun executeB(variable: Int) {
        viewModelScope.launch(context = Job(supervisorB).plus(customExecutor)) {
            taskB(variable)
        }
    }

    private suspend fun taskA(variable: Int) = coroutineScope {
        val sortingResult = async { randomListSortedAndAggregated(variable.times(10000)) }
        taskARunning.postValue(true)
        taskACounter++
        val startTimestamp: Long = System.currentTimeMillis()
        Log.d("Task_A", "Started($variable) at $startTimestamp")
        try {
            val taskResult = sortingResult.await()
            val processDuration = System.currentTimeMillis().minus(startTimestamp)
            Log.d(
                "Task_A",
                "Finished($variable) in time: $processDuration with result: $taskResult"
            )
        } catch (e: Exception) {
            val processDuration = System.currentTimeMillis().minus(startTimestamp)
            if (e is CancellationException) Log.d(
                "Task_A",
                "Cancelled($variable) after $processDuration"
            )
            else Log.d("Task_A", "Error($variable), Caught exception during sorting: $e")
        } finally {
            decreaseCounterA()
        }
    }

    private suspend fun taskB(variable: Int) = coroutineScope {
        val awaitBubbleSort = async { randomListBubbleSortLastItem(variable.times(1000)) }
        taskBRunning.postValue(true)
        taskBCounter++
        val startTimestamp: Long = System.currentTimeMillis()
        Log.d("Task_B", "Started($variable) at $startTimestamp")
        try {
            val result = awaitBubbleSort.await()
            val processDuration = System.currentTimeMillis().minus(startTimestamp)
            Log.d("Task_B", "Finished($variable) in time: $processDuration with result $result")
        } catch (e: Exception) {
            val processDuration = System.currentTimeMillis().minus(startTimestamp)
            if (e is CancellationException) Log.d(
                "Task_B",
                "Cancelled($variable) after $processDuration"
            )
            else Log.d("Task_B", "Error($variable), Caught exception during sorting: $e")
        } finally {
            decreaseCounterB()
        }
    }

    private fun randomListSortedAndAggregated(variable: Int): Long {
        val randomList = List(variable) { Random.nextInt(1, 10) }
        val fibonacciList = randomList.map { value ->
            val seq = generateSequence(
                Pair(0L, 1L),
                { Pair(it.second, it.first + it.second) }).map { it.second }
            seq.take(value).last()
        }
        val sorted = fibonacciList.sorted()
        return sorted.sum()
    }

    private fun randomListBubbleSortLastItem(variable: Int): Int {
        val arr = List(variable) { Random.nextInt(1, variable) }.toIntArray()
        val n = arr.size
        for (i in 0 until n - 1) for (j in 0 until n - i - 1) if (arr[j] > arr[j + 1]) {
            val temp = arr[j]
            arr[j] = arr[j + 1]
            arr[j + 1] = temp
        }
        return arr.last()
    }

    private fun decreaseCounterA() {
        taskACounter--
        Log.d("Task_A", "counter value: $taskACounter")
        if (taskACounter <= 0) taskARunning.postValue(false)
    }

    private fun decreaseCounterB() {
        taskBCounter--
        Log.d("Task_B", "counter value: $taskBCounter")
        if (taskACounter <= 0) taskBRunning.postValue(false)
    }

    private fun setupMediator() {
        tasksInProgress.addSource(taskARunning) {
            tasksInProgress.value = !(taskBRunning.value != true && !it)
        }
        tasksInProgress.addSource(taskBRunning) {
            tasksInProgress.value = !(taskARunning.value != true && !it)
        }
    }

    fun onAButtonClicked(variableA: String) {
        variableA.toIntOrNull()?.let { executeA(it) }
    }

    fun onBButtonClicked(variableB: String) {
        variableB.toIntOrNull()?.let { executeB(it) }
    }

    fun setThreadPoolClicked(seekBarValue: Int) {
        val threads = if (seekBarValue == 0) 1 else seekBarValue
        customExecutor = Executors.newFixedThreadPool(threads).asCoroutineDispatcher()
    }

    fun onCancelAClicked() {
        supervisorA.cancelChildren()
    }

    fun onCancelBClicked() {
        supervisorB.cancelChildren()
    }
}

