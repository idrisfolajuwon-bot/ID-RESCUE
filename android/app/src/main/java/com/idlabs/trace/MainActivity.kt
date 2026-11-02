package com.idlabs.trace

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.IDN
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg=Color.rgb(6,15,29); private val panel=Color.rgb(15,29,48)
    private val mint=Color.rgb(76,226,190); private val white=Color.WHITE
    private val muted=Color.rgb(157,177,199); private val amber=Color.rgb(255,190,92)
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var output: TextView
    private lateinit var radar: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=bg; window.navigationBarColor=bg; home() }

    private fun base(title:String, sub:String) {
        val scroll=ScrollView(this)
        root=LinearLayout(this).apply { orientation=1; setPadding(20,20,20,30); setBackgroundColor(bg) }
        scroll.addView(root); setContentView(scroll)
        root.addView(txt("IDRIS  /  I D LABS",12,mint,true))
        root.addView(txt("TRACE X",30,white,true))
        root.addView(txt(title.uppercase(),11,mint,true))
        root.addView(txt(sub,13,muted,false).apply { setPadding(0,5,0,18) })
    }
    private fun txt(s:String,size:Int,color:Int,bold:Boolean=false)=TextView(this).apply { text=s; textSize=size.toFloat(); setTextColor(color); if(bold) typeface=Typeface.DEFAULT_BOLD; setPadding(0,5,0,5) }
    private fun card():LinearLayout=LinearLayout(this).apply { orientation=1; setPadding(16,14,16,14); background=GradientDrawable().apply { setColor(panel); cornerRadius=22f } }
    private fun button(s:String, action:()->Unit)=Button(this).apply { text=s; setTextColor(bg); textSize=12f; isAllCaps=false; backgroundTintList=android.content.res.ColorStateList.valueOf(mint); setOnClickListener { action() } }
    private fun field(hint:String)=EditText(this).apply { this.hint=hint; setHintTextColor(muted); setTextColor(white); textSize=15f; setSingleLine(true); setPadding(14,12,14,12); background=GradientDrawable().apply { setColor(Color.rgb(22,41,63)); cornerRadius=14f } }
    private fun section(s:String){ root.addView(txt(s,12,mint,true).apply { setPadding(0,18,0,8) }) }
    private fun home() {
        base("Digital investigation command centre","Inspect suspicious links and messages without opening them.")
        val hero=card(); hero.addView(txt("INVESTIGATE BEFORE YOU TAP",18,white,true)); hero.addView(txt("A defensive toolkit for suspicious links, messages and QR destinations. No submitted website is opened.",13,muted)); root.addView(hero)
        section("INTELLIGENCE TOOLS")
        addTile("01  LINK INTELLIGENCE","DNS + domain registration checks, URL structure signals",::linkScreen)
        addTile("02  MESSAGE INTELLIGENCE","Spot pressure tactics, OTP requests and payment traps",::messageScreen)
        addTile("03  QR DESTINATION CHECK","Paste decoded QR text and inspect its destination",::qrScreen)
        addTile("04  CASE FILES","Review locally saved investigation notes",::casesScreen)
        addTile("05  LIVE THREAT RADAR","Evidence map of checks actually performed",::radarScreen)
        root.addView(txt("PRIVACY: scans run only when you request them. Case notes stay on this device.",11,muted).apply { setPadding(0,20,0,0) })
    }
    private fun addTile(title:String,desc:String,action:()->Unit){ val c=card(); c.addView(txt(title,15,white,true)); c.addView(txt(desc,12,muted)); c.addView(button("OPEN TOOL  →",action)); val p=LinearLayout.LayoutParams(-1,-2); p.bottomMargin=10; root.addView(c,p) }
    private fun linkScreen(){
        base("Link intelligence","The app queries public DNS and RDAP services. It never visits the submitted site.")
        input=field("Paste a full link or domain"); root.addView(input)
        root.addView(button("RUN INVESTIGATION"){ val raw=input.text.toString().trim(); if(raw.isBlank()){show("Enter a link first.");return@button}; scanLink(raw) })
        output=txt("Waiting for a link to inspect.",13,white); root.addView(card().apply{addView(output)})
        radar=txt("",12,muted); root.addView(radar)
    }
    private fun scanLink(raw:String){
        val parsed=try { val fixed=if(raw.contains("://")) raw else "https://$raw"; URL(fixed) } catch(e:Exception){ show("This does not look like a valid web address."); return }
        val host=parsed.host?.lowercase()?.trimEnd('.') ?: ""; if(parsed.protocol !in listOf("http","https") || host.isBlank() || host.contains("@")){show("Unsupported or malformed address.");return}
        if(host=="localhost" || host.endsWith(".local") || host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))){show("Local or IP-based destination. No external lookup performed.");return}
        val flags=mutableListOf<String>()
        if(parsed.protocol!="https") flags.add("Uses HTTP rather than HTTPS")
        if(raw.contains("@")) flags.add("Contains an @ sign that can disguise the displayed destination")
        if(host.startsWith("xn--") || host.split(".").any{it.startsWith("xn--")}) flags.add("Contains an internationalised/punycode hostname")
        if(host.length>55) flags.add("Unusually long hostname")
        if(listOf("login","verify","wallet","secure","claim","bonus","support","update").any{host.contains(it)}) flags.add("Contains a common lure word (not proof of fraud)")
        output.text="Checking $host …"
        worker.execute {
            val findings=mutableListOf<String>(); findings.add("URL structure: "+if(flags.isEmpty()) "No basic warning pattern detected" else flags.joinToString("; "))
            val dns=fetch("https://dns.google/resolve?name="+java.net.URLEncoder.encode(host,"UTF-8")+"&type=A")
            if(dns!=null) try { val j=JSONObject(dns); val answers=j.optJSONArray("Answer"); val ips=mutableListOf<String>(); if(answers!=null) for(i in 0 until answers.length()){val a=answers.getJSONObject(i); if(a.optInt("type")==1) ips.add(a.optString("data"))}; findings.add("Public DNS: "+if(ips.isEmpty()) "No A record returned" else ips.distinct().joinToString()) } catch(_:Exception){findings.add("Public DNS: response could not be interpreted")} else findings.add("Public DNS: lookup unavailable")
            val rdap=fetch("https://rdap.org/domain/"+host)
            if(rdap!=null) try { val j=JSONObject(rdap); val events=j.optJSONArray("events"); var date="Registration date not supplied"; if(events!=null) for(i in 0 until events.length()){val ev=events.getJSONObject(i); if(ev.optString("eventAction").contains("registration")) date=ev.optString("eventDate")}; findings.add("RDAP: $date") } catch(_:Exception){findings.add("RDAP: no usable registration record")} else findings.add("RDAP: lookup unavailable")
            findings.add("Not checked: threat-feed reputation, redirect chain, certificate history and site content.")
            runOnUiThread { output.text=findings.joinToString("\n\n"); saveCase("LINK: $host\n"+findings.joinToString("\n")); radar.text="RADAR NODES\n• Submitted host: $host\n• DNS: "+if(dns!=null) "queried" else "unavailable"+"\n• RDAP: "+if(rdap!=null) "queried" else "unavailable" }
        }
    }
    private fun fetch(address:String):String?=try { val c=URL(address).openConnection() as HttpURLConnection; c.connectTimeout=7000; c.readTimeout=7000; c.requestMethod="GET"; c.setRequestProperty("Accept","application/json"); val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream; val result=stream?.bufferedReader()?.use{it.readText().take(120000)}; c.disconnect(); result }catch(_:Exception){null}
    private fun messageScreen(){
        base("Message intelligence","Paste a suspicious SMS, WhatsApp or email. Analysis happens on-device.")
        val box=EditText(this).apply { hint="Paste message text here…"; setHintTextColor(muted); setTextColor(white); minLines=5; gravity=Gravity.TOP; setPadding(14,14,14,14); background=GradientDrawable().apply{setColor(Color.rgb(22,41,63));cornerRadius=14f} }; root.addView(box)
        output=txt("No message analysed yet.",13,white); root.addView(card().apply{addView(output)})
        root.addView(button("ANALYSE MESSAGE"){ val s=box.text.toString().lowercase(); if(s.isBlank()){output.text="Paste a message first.";return@button}; val checks=listOf("urgent","immediately","within 24","otp","one-time password","pin","password","claim your prize","release fee","processing fee","send money","verify your account","account suspended","click this link"); val hits=checks.filter{s.contains(it)}; output.text=if(hits.isEmpty()) "No listed pressure/credential patterns found. This does not prove the message is genuine." else "WARNING SIGNALS FOUND\n"+hits.joinToString("\n• ","• ")+"\n\nThese are clues only, not proof. Confirm through the organisation's official app or known contact."; saveCase("MESSAGE ANALYSIS\nSignals: "+hits.joinToString()) })
    }
    private fun qrScreen(){ base("QR destination check","Paste the text decoded by your camera or QR reader. Do not open it first."); input=field("Paste QR destination or text"); root.addView(input); output=txt("QR text has not been checked.",13,white); root.addView(card().apply{addView(output)}); root.addView(button("INSPECT DESTINATION"){val s=input.text.toString().trim(); if(s.isBlank()){output.text="Paste QR text first."}else if(s.startsWith("http://")||s.startsWith("https://")){output.text="This QR contains a web link. Open Link Intelligence to inspect it without visiting the site."; }else{output.text="QR content is not a web link. Treat payment requests, contact details and login instructions cautiously."}; saveCase("QR CHECK\n"+s.take(500))}) }
    private fun casesScreen(){base("Case files","Saved locally on this device."); val prefs=getSharedPreferences("cases",0); val all=prefs.getString("items","")?: ""; if(all.isBlank())root.addView(txt("No saved cases yet.",14,muted)) else root.addView(card().apply{addView(txt(all,12,white))}); root.addView(button("CLEAR ALL CASES"){prefs.edit().remove("items").apply(); casesScreen()})}
    private fun saveCase(s:String){val p=getSharedPreferences("cases",0); val old=p.getString("items","")?: ""; p.edit().putString("items",s+"\n────────────\n"+old.take(12000)).apply()}
    private fun radarScreen(){base("Live threat radar","Only evidence from checks you actually ran is shown. No invented connections."); val c=card(); c.addView(txt("CENTRE NODE",11,mint,true)); c.addView(txt("Submitted link / message",17,white,true)); c.addView(txt("Connected evidence nodes",11,mint,true)); c.addView(txt("DNS lookup • RDAP registration • local text signals",13,muted)); c.addView(txt("Not connected: commercial threat feeds, redirect tracing, certificate transparency.",12,amber)); root.addView(c); root.addView(txt("Run a link or message investigation to populate evidence in the relevant tool.",13,muted))}
    private fun show(s:String){android.widget.Toast.makeText(this,s,Toast.LENGTH_LONG).show()}
    override fun onDestroy(){super.onDestroy();worker.shutdown()}
}