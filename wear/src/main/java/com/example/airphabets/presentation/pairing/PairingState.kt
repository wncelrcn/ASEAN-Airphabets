package com.example.airphabets.presentation.pairing

/**
 * Represents the different states of the watch-phone pairing process
 */
sealed class PairingState {
    /** Initial state - prompting user to open mobile app */
    object Prompt : PairingState()
    
    /** Checking connection with phone */
    object Checking : PairingState()
    
    /** Successfully connected to phone */
    object Success : PairingState()
    
    /** Connection lost or error occurred */
    data class Error(val message: String) : PairingState()
    
    /** Bluetooth is turned off */
    object BluetoothOff : PairingState()
    
    /** Max retry attempts reached - show skip/try again options */
    data class MaxRetriesReached(val attemptCount: Int) : PairingState()
    
    /** Waiting for the phone user to accept the pairing request */
    object WaitingForAcceptance : PairingState()
    
    /** Phone user declined the pairing request */
    object Declined : PairingState()
}
