package com.example.tiktoklike

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** id dipakai sebagai nama profil WebView (browser terpisah), label = nama tampilan. */
data class Account(val id: String, val label: String)

class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("accounts", Context.MODE_PRIVATE)

    fun all(): List<Account> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Account(o.getString("id"), o.getString("label"))
        }
    }

    fun add(label: String): Account {
        val next = prefs.getInt("next", 1)
        val acc = Account("acc_$next", label)
        save(all() + acc)
        prefs.edit().putInt("next", next + 1).apply()
        return acc
    }

    fun remove(id: String) = save(all().filter { it.id != id })

    private fun save(list: List<Account>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("label", it.label)) }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
