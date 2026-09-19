package com.narvyn.suraksha

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.view.WindowInsets

/** Shared-phone profiles separate local learning records; switching is intentionally not authentication. */
class WorkerProfilesActivity:Activity() {
    private lateinit var store:Store
    private val hi get()=store.hi
    private fun t(en:String,hindi:String)=if(hi)hindi else en
       // API 33+ uses AppBackNavigation and PagedPanel callbacks; retain this fallback for API 29–32.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Deprecated("Android 10–12 compatibility") override fun onBackPressed(){if(!popContentPage())super.onBackPressed()}
 override fun onCreate(state:Bundle?) {
        super.onCreate(state);store=Store(this);render()
    }
    private fun render() {
        val root=column(20).apply { setBackgroundColor(Palette.canvas) }
        root.setOnApplyWindowInsetsListener { view,insets ->
            if(android.os.Build.VERSION.SDK_INT>=30) {
                val bars=insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(dp(20)+bars.left,dp(20)+bars.top,dp(20)+bars.right,dp(20)+bars.bottom)
            } else view.setPadding(dp(20)+insets.systemWindowInsetLeft,dp(20)+insets.systemWindowInsetTop,dp(20)+insets.systemWindowInsetRight,dp(20)+insets.systemWindowInsetBottom)
            insets
        }
        root.add(label(t("Choose a learner","सीखने वाला चुनें"),26f,Palette.ink,true).asHeading(),bottom=10)
        root.add(label(t("Each learner has separate progress on this phone.","इस फ़ोन पर हर व्यक्ति की सीखने की प्रगति अलग रहती है।"),16f),bottom=16)
        root.add(card(Palette.soft).apply {
            add(label(t("Device-local profiles","इस फ़ोन की प्रोफ़ाइल"),17f,Palette.ink,true),bottom=6)
            add(label(t("Anyone using this unlocked app can switch profiles. PIN protection and identity verification are not available.","इस खुले ऐप का उपयोग करने वाला कोई भी प्रोफ़ाइल बदल सकता है। पिन सुरक्षा और पहचान सत्यापन उपलब्ध नहीं हैं।"),14f,Palette.muted))
        },bottom=16)
        store.profiles().forEach { profile ->
            val id=profile.getString("id");val current=id==store.workerId
            val name=profile.getString("name").ifBlank { t("Original learner","पहले सीखने वाला व्यक्ति") }
            val sector=sectorLabel(profile.getString("sector"))
            root.add(card().apply {
                add(label(name,21f,Palette.ink,true),bottom=5)
                add(label("$sector · "+t("Profile","प्रोफ़ाइल")+" "+id.take(8),14f,Palette.muted),bottom=12)
                if(current)add(label(t("Currently selected","अभी चुनी गई"),15f,Palette.success,true))
                else add(action(t("Use this profile","यह प्रोफ़ाइल चुनें"),false) {
                    store.switchProfile(id);setResult(RESULT_OK);finish()
                }.apply { contentDescription=t("Use profile for $name","$name की प्रोफ़ाइल चुनें") })
            },bottom=12)
        }
        root.add(action(t("Add a learner","नया व्यक्ति जोड़ें")) { createProfile() },top=8,bottom=10)
        root.add(action(t("Back to learning","सीखने पर वापस जाएँ"),false,role=ActionRole.NEUTRAL) { finish() })
        setContentView(paged(root,hi))
    }
    private fun sectorLabel(value:String):String {
        val index=Store.SECTORS.indexOf(value).coerceAtLeast(0)
        return if(hi)listOf("क्षेत्र नहीं चुना","खनन","इस्पात","अभ्रक","अन्य")[index]else Store.SECTORS[index]
    }
    private fun createProfile() {
        val form=column(20)
        form.add(label(t("Learner name","सीखने वाले का नाम"),15f,Palette.ink,true),bottom=4)
        val name=EditText(this).apply { hint=t("Enter a name","नाम लिखें");setSingleLine();filters=arrayOf(InputFilter.LengthFilter(80));minimumHeight=dp(56);contentDescription=t("Learner name","सीखने वाले का नाम") }
        form.add(name,bottom=12)
        form.add(label(t("Work sector","कार्य क्षेत्र"),15f,Palette.ink,true),bottom=4)
        val sector=Spinner(this).apply { adapter=ArrayAdapter(this@WorkerProfilesActivity,android.R.layout.simple_spinner_dropdown_item,Store.SECTORS.map(::sectorLabel));minimumHeight=dp(56);contentDescription=t("Work sector","कार्य क्षेत्र") }
        form.add(sector)
        val dialog=AlertDialog.Builder(this).setTitle(t("Create a local profile","इस फ़ोन पर प्रोफ़ाइल बनाएँ"))
            .setView(paged(form,hi)).setPositiveButton(t("Create and switch","बनाएँ और चुनें"),null)
            .setNegativeButton(t("Cancel","रद्द करें"),null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value=name.text.toString().trim()
                if(value.isBlank()) { name.error=t("Enter a name","नाम लिखें");name.requestFocus();return@setOnClickListener }
                val id=store.createProfile(value,Store.SECTORS[sector.selectedItemPosition])
                store.switchProfile(id);dialog.dismiss();setResult(RESULT_OK);finish()
            }
        }
        dialog.show()
    }
    override fun onDestroy() { if(::store.isInitialized)store.close();super.onDestroy() }
}
