package tw.nekomimi.nekogram.database

import android.content.SharedPreferences
import org.dizitart.no2.*
import org.dizitart.no2.collection.Document
import org.dizitart.no2.collection.NitriteCollection
import org.dizitart.no2.collection.UpdateOptions
import org.dizitart.no2.filters.Filter
import org.dizitart.no2.filters.FluentFilter.where
import org.dizitart.no2.index.IndexOptions
import org.dizitart.no2.index.IndexType
import org.telegram.messenger.FileLog
import tw.nekomimi.nekogram.utils.UIUtil

class DbPref(val connection: NitriteCollection) : SharedPreferences {

    init {
        if (!connection.hasIndex("key")) {
            connection.createIndex("key", IndexOptions.indexOptions(IndexType.Unique))
        }
    }

    val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    val isEmpty get() = connection.find().limit(1).count() == 0

    private inline fun <reified T> getAs(key: String, defValue: T): T {
        connection.find(where("key").eq(key)).apply {
            runCatching {
                return first().get("value", T::class.java)
            }
        }
        return defValue
    }

    override fun contains(key: String): Boolean {
        return connection.find(where("key").eq(key)).limit(1).count() > 0
    }

    override fun getBoolean(key: String, defValue: Boolean) = getAs(key, defValue)

    override fun getInt(key: String, defValue: Int) = getAs(key, defValue)

    override fun getAll(): MutableMap<String, *> {
        val allValues = HashMap<String, Any>()
        connection.find().forEach {
            allValues[it.get("key", String::class.java)] = it["value"]
        }
        return allValues
    }

    override fun getLong(key: String, defValue: Long) = getAs(key, defValue)

    override fun getFloat(key: String, defValue: Float) = getAs(key, defValue)

    override fun getString(key: String, defValue: String?) = getAs(key, defValue)

    override fun getStringSet(key: String, defValues: MutableSet<String>?) = getAs(key, defValues)

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listener?.apply { listeners.add(this) }
    }

    override fun edit(): PrefEditor {
        return PrefEditor()
    }

    inner class PrefEditor : SharedPreferences.Editor {

        private var clear = false
        private val toRemove = HashSet<String>()
        private val toApply = HashMap<String, Any?>()

        override fun clear(): PrefEditor {
            clear = true
            return this
        }

        override fun putLong(key: String, value: Long): PrefEditor {
            toApply[key] = value
            return this
        }

        override fun putInt(key: String, value: Int): PrefEditor {
            toApply[key] = value
            return this
        }

        override fun remove(key: String): PrefEditor {
            toApply.remove(key)
            toRemove.add(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): PrefEditor {
            toApply[key] = value
            return this
        }

        override fun putStringSet(key: String, values: MutableSet<String>?): PrefEditor {
            toApply[key] = values
            return this
        }

        override fun putFloat(key: String, value: Float): PrefEditor {
            toApply[key] = value
            return this
        }

        override fun putString(key: String, value: String?): PrefEditor {
            toApply[key] = value
            return this
        }

        override fun commit(): Boolean {
            try {
                if (clear) {
                    connection.remove(Filter.ALL)
                } else {
                    toRemove.forEach {
                        connection.remove(where("key").eq(it))
                    }
                }
                toApply.forEach { (key, value) ->
                    if (value == null) {
                        connection.remove(where("key").eq(key))
                    } else {
                        connection.update(where("key").eq(key), Document.createDocument(mapOf(
                            "key" to key,
                            "value" to value
                        )), UpdateOptions.updateOptions(true))
                    }
                }
                return true
            } catch (ex: Exception) {
                FileLog.e(ex)
                return false
            }
        }

        override fun apply() {
            UIUtil.runOnIoDispatcher(Runnable {  commit() } )
        }

    }

}