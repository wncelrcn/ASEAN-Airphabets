package com.example.airphabets.common

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessageService(context: Context) : MessageClient.OnMessageReceivedListener {

    private val messageClient: MessageClient? = try {
        Wearable.getMessageClient(context)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        try {
            messageClient?.addListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendMessage(node: Node, path: String, message: String) {
        try {
            messageClient?.sendMessage(node.id, path, message.toByteArray())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        _message.value = String(messageEvent.data)
    }
}
