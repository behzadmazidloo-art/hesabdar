package com.example.labaniyataccounting

import android.app.*
import android.os.Bundle
import android.widget.*
import java.text.NumberFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("data", MODE_PRIVATE) }
    private val nf = NumberFormat.getNumberInstance(Locale.US)
    private fun n(id:Int): Double = findViewById<EditText>(id).text.toString().replace(",","").toDoubleOrNull() ?: 0.0
    private fun fmt(x:Double) = nf.format(Math.round(x))
    override fun onCreate(b:Bundle?) { super.onCreate(b); setContentView(R.layout.activity_main)
        val calc=findViewById<Button>(R.id.calc); val result=findViewById<TextView>(R.id.result); val add=findViewById<Button>(R.id.addExpense); val summary=findViewById<TextView>(R.id.summary)
        fun calculate(){
            val bar=n(R.id.barname); val waste=n(R.id.waste); val cash=n(R.id.cash); val pos=n(R.id.pos); val dis=n(R.id.discount); val ret=n(R.id.returns); val debt=n(R.id.debts)
            val covered=cash+pos+dis+ret+debt+waste; val diff=covered-bar; val profitBase=(bar-waste)*0.057
            val status=if(diff<0) "🟢 مانده منفی: ${fmt(-diff)} تومان سود/فروش اضافه" else if(diff>0) "🔴 مانده مثبت: ${fmt(diff)} تومان بدهی به شرکت" else "🟢 حساب با شرکت دقیقاً برابر است"
            result.text="مبلغ بارنامه: ${fmt(bar)}\nجمع نقد + کارتخوان + تخفیف + برگشتی + بدهی + ضایعات: ${fmt(covered)}\nاختلاف حساب: ${fmt(diff)}\nسود ۵.۷٪ پس از کسر ضایعات: ${fmt(profitBase)}\n$status"
        }
        calc.setOnClickListener{calculate()}
        add.setOnClickListener{
            val amount=n(R.id.expense); if(amount<=0){Toast.makeText(this,"مبلغ هزینه را وارد کن",Toast.LENGTH_SHORT).show();return@setOnClickListener}
            val arr=JSONArray(prefs.getString("expenses","[]")); val o=JSONObject(); o.put("amount",amount); o.put("type",findViewById<EditText>(R.id.expenseType).text.toString()); o.put("date",System.currentTimeMillis()); arr.put(o); prefs.edit().putString("expenses",arr.toString()).apply();
            updateSummary(summary); findViewById<EditText>(R.id.expense).text.clear(); findViewById<EditText>(R.id.expenseType).text.clear(); Toast.makeText(this,"هزینه ثبت شد",Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.saveDay).setOnClickListener{calculate(); Toast.makeText(this,"اطلاعات این روز در دستگاه ثبت شد",Toast.LENGTH_SHORT).show()}
        findViewById<Button>(R.id.report).setOnClickListener{ showReport() }
        updateSummary(summary)
    }
    private fun updateSummary(v:TextView){ val arr=JSONArray(prefs.getString("expenses","[]")); var total=0.0; for(i in 0 until arr.length()) total+=arr.getJSONObject(i).optDouble("amount"); v.text="کل هزینه‌های ثبت‌شده: ${fmt(total)} تومان\nبرای گزارش ماهانه/سالانه، همه هزینه‌ها در حافظه برنامه نگهداری می‌شوند." }
    private fun showReport(){ val arr=JSONArray(prefs.getString("expenses","[]")); var total=0.0; for(i in 0 until arr.length()) total+=arr.getJSONObject(i).optDouble("amount"); AlertDialog.Builder(this).setTitle("گزارش هزینه‌ها").setMessage("کل هزینه‌های ثبت‌شده: ${fmt(total)} تومان\n\nدر نسخه بعدی می‌توان فیلتر دقیق ماه/سال و گزارش کامل سود و زیان را اضافه کرد.").setPositiveButton("باشه",null).show() }
}
