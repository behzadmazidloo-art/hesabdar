package com.example.labaniyataccounting

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("hesabyar_v2", MODE_PRIVATE) }
    private val blue = Color.rgb(31, 63, 155)
    private val blue2 = Color.rgb(48, 83, 178)
    private val deepBlue = Color.rgb(20, 48, 125)
    private val bg = Color.rgb(246, 248, 252)
    private val text = Color.rgb(48, 50, 58)
    private val gray = Color.rgb(105, 108, 116)
    private val green = Color.rgb(32, 132, 85)
    private val red = Color.rgb(190, 55, 65)
    private val white = Color.WHITE

    data class Entry(val id: Long, val category: String, val amount: Long, val date: String, val name: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showDashboard()
    }

    // ---------- Dashboard ----------
    private fun showDashboard() {
        val r = baseRoot("حسابیار لبنیات", false)
        r.addView(tv("مدیریت حساب و تسویه روزانه", 15f, gray, Gravity.CENTER), lp(0, 8))

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 22, 18, 18)
        }
        grid.addView(squareCard("حساب شرکت", "بارنامه و تسویه شرکت", "▣", { showCompany() }), lp(0, 12))
        grid.addView(squareCard("هزینه ها", "خودرو، کارگر و بیمه", "▤", { showExpenses() }), lp(0, 12))
        grid.addView(squareCard("بدهی مغازه دارها", "مبلغ بدهی و تسویه مغازه ها", "▥", { showShopDebts() }), lp(0, 12))
        r.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(tv("تمام مبالغ به ریال • تاریخ‌ها شمسی", 12f, gray, Gravity.CENTER), lp(0, 6))
        setContentView(r)
    }

    private fun squareCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 20, 18, 20)
            background = rounded(white, 22, Color.rgb(218, 225, 242))
            elevation = 7f
            setOnClickListener { click() }
        }
        val iconBox = TextView(this).apply {
            text = icon; textSize = 30f; setTextColor(white); gravity = Gravity.CENTER
            background = rounded(blue, 18, null)
        }
        box.addView(iconBox, LinearLayout.LayoutParams(72, 72))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 0, 0, 0) }
        val t = tv(title, 20f, deepBlue, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        col.addView(t, lp())
        col.addView(tv(subtitle, 13f, gray, Gravity.RIGHT), lp(0, 5))
        box.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        box.addView(tv("‹", 35f, blue, Gravity.CENTER), LinearLayout.LayoutParams(40, -1))
        return box
    }

    // ---------- Company ----------
    private fun showCompany() {
        val r = baseRoot("حساب شرکت", true)
        r.addView(sectionTitle("ثبت و کنترل تسویه با شرکت"), lp())

        val debtTotal = shopDebtTotal()
        r.addView(infoBox("بدهی مغازه دارها (خودکار)", "${money(debtTotal)} ریال"), lp(0, 6))

        val cats = listOf("مبلغ بارنامه", "ضایعات", "کارتخوان", "پول نقد", "تخفیفات", "برگشتی")
        cats.forEach { c ->
            r.addView(actionRow(c) { companyAddDialog(c) }, lp(0, 7))
            addEntriesForCategory(r, "company", c)
        }

        r.addView(sectionTitle("خلاصه حساب شرکت"), lp(0, 14))
        r.addView(companySummary(), lp(0, 6))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    private fun companySummary(): LinearLayout {
        val barnameh = latestAmount("مبلغ بارنامه")
        val waste = latestAmount("ضایعات")
        val card = sumCategory("کارتخوان")
        val cash = sumCategory("پول نقد")
        val discount = sumCategory("تخفیفات")
        val returns = sumCategory("برگشتی")
        val debts = shopDebtTotal()
        val settlement = waste + card + cash + discount + returns + debts
        val diff = settlement - barnameh
        val profit = ((barnameh - waste).coerceAtLeast(0L) * 57L) / 1000L

        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); background = rounded(white, 18, Color.rgb(215, 222, 240)) }
        addSummaryLine(b, "مبلغ بارنامه", barnameh, deepBlue)
        addSummaryLine(b, "ضایعات", waste, text)
        addSummaryLine(b, "کارتخوان", card, text)
        addSummaryLine(b, "پول نقد", cash, text)
        addSummaryLine(b, "بدهی مغازه دارها", debts, text)
        addSummaryLine(b, "تخفیفات", discount, text)
        addSummaryLine(b, "برگشتی", returns, text)
        addSummaryLine(b, "جمع تسویه", settlement, blue)
        val label = when {
            diff > 0 -> "سود اضافه حساب"
            diff < 0 -> "بدهی باقی مانده"
            else -> "تسویه کامل"
        }
        val amount = kotlin.math.abs(diff)
        addSummaryLine(b, label, amount, if (diff < 0) red else if (diff > 0) green else green)
        addSummaryLine(b, "سود پایه ۵.۷٪ پس از کسر ضایعات", profit, green)
        return b
    }

    private fun addSummaryLine(parent: LinearLayout, label: String, amount: Long, color: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 5, 0, 5); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row.addView(tv(label, 14f, gray, Gravity.RIGHT), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv("${money(amount)} ریال", 15f, color, Gravity.LEFT), LinearLayout.LayoutParams(-2, -2))
        parent.addView(row)
    }

    // ---------- Expenses ----------
    private fun showExpenses() {
        val r = baseRoot("هزینه ها", true)
        r.addView(sectionTitle("هزینه های خودرو"), lp())
        listOf("گازوئیل", "تعمیرات", "خرید قطعات", "تعمیر یخچال").forEach { c ->
            r.addView(actionRow(c) { expenseAddDialog(c) }, lp(0, 7))
            addEntriesForCategory(r, "expense", c)
        }
        r.addView(sectionTitle("هزینه کارگر"), lp(0, 12))
        r.addView(actionRow("نام کارگر و مبلغ") { expenseAddDialog("هزینه کارگر", true) }, lp(0, 7))
        addEntriesForCategory(r, "expense", "هزینه کارگر")
        r.addView(sectionTitle("هزینه بیمه"), lp(0, 12))
        r.addView(actionRow("ثبت هزینه بیمه") { expenseAddDialog("هزینه بیمه") }, lp(0, 7))
        addEntriesForCategory(r, "expense", "هزینه بیمه")
        r.addView(infoBox("جمع کل هزینه ها", "${money(expenseTotal())} ریال"), lp(0, 14))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    // ---------- Shop debts ----------
    private fun showShopDebts() {
        val r = baseRoot("بدهی مغازه دارها", true)
        r.addView(sectionTitle("ثبت نام فروشگاه، مبلغ بدهی و تسویه"), lp())
        r.addView(infoBox("جمع بدهی باقی مانده", "${money(shopDebtTotal())} ریال"), lp(0, 6))
        r.addView(actionRow("➕ افزودن فروشگاه") { shopDebtDialog(null) }, lp(0, 9))

        val arr = readArray("shop_debts")
        if (arr.length() == 0) r.addView(empty("هنوز فروشگاهی ثبت نشده است."), lp(0, 14))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("id")
            val name = o.optString("name")
            val amount = o.optLong("amount")
            val date = o.optString("date")
            val status = if (amount == 0L) "تسویه شده" else "مانده: ${money(amount)} ریال"
            val row = shopRow(name, status, date, amount,
                { shopDebtDialog(id) },
                { if (amount > 0) settlementDialog(id) else toast("این حساب قبلاً تسویه شده است") },
                { deleteById("shop_debts", id); showShopDebts() })
            r.addView(row, lp(0, 9))
        }
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    private fun shopRow(name: String, status: String, date: String, amount: Long, edit: () -> Unit, settle: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12); background = rounded(white, 18, Color.rgb(220, 226, 240)); elevation = 2f }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val title = tv(name, 17f, text, Gravity.RIGHT); title.typeface = Typeface.DEFAULT_BOLD
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(smallButton("ویرایش", edit), LinearLayout.LayoutParams(78, 40))
        row.addView(top, lp())
        row.addView(tv(status, 15f, if (amount == 0L) green else red, Gravity.RIGHT), lp(0, 5))
        row.addView(tv("تاریخ ثبت: $date", 12f, gray, Gravity.RIGHT), lp(0, 3))
        if (amount > 0) row.addView(actionRow("ثبت مبلغ تسویه شده", settle), lp(0, 7))
        row.addView(smallButton("حذف حساب", delete), LinearLayout.LayoutParams(-1, 38).apply { topMargin = 5 })
        return row
    }

    private fun shopDebtDialog(existingId: Long?) {
        val old = existingId?.let { findById("shop_debts", it) }
        val name = EditText(this).apply { hint = "نام فروشگاه / مغازه دار"; setText(old?.name ?: "") }
        val amount = EditText(this).apply { hint = "مبلغ بدهی به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()) }
        val box = vertical(name, amount, date)
        AlertDialog.Builder(this)
            .setTitle(if (old == null) "افزودن فروشگاه" else "ویرایش حساب فروشگاه")
            .setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val n = name.text.toString().trim(); val v = parseMoney(amount.text.toString())
                if (n.isEmpty() || v < 0) toast("نام فروشگاه و مبلغ را درست وارد کنید")
                else saveEntry("shop_debts", Entry(existingId ?: System.currentTimeMillis(), "", v, date.text.toString(), n))
                showShopDebts()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun settlementDialog(id: Long) {
        val old = findById("shop_debts", id) ?: return
        val amount = EditText(this).apply { hint = "مبلغی که فروشگاه تسویه کرده"; inputType = InputType.TYPE_CLASS_NUMBER; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ تسویه شمسی"; setText(todayJalali()) }
        val box = vertical(amount, date)
        AlertDialog.Builder(this)
            .setTitle("ثبت تسویه ${old.name}")
            .setMessage("مانده فعلی: ${money(old.amount)} ریال")
            .setView(box)
            .setPositiveButton("ثبت تسویه") { _, _ ->
                val payment = parseMoney(amount.text.toString())
                when {
                    payment <= 0 -> toast("مبلغ تسویه را وارد کنید")
                    payment > old.amount -> toast("مبلغ تسویه نمی‌تواند بیشتر از بدهی باشد")
                    else -> {
                        val newBalance = old.amount - payment
                        updateShopDebt(id, newBalance, date.text.toString(), payment)
                        toast("تسویه ثبت شد")
                        showShopDebts()
                    }
                }
            }.setNegativeButton("انصراف", null).show()
    }

    private fun updateShopDebt(id: Long, newBalance: Long, date: String, payment: Long) {
        val arr = readArray("shop_debts")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("id") == id) {
                o.put("amount", newBalance)
                o.put("date", date)
                val payments = o.optJSONArray("payments") ?: JSONArray()
                payments.put(JSONObject().apply { put("amount", payment); put("date", date) })
                o.put("payments", payments)
                arr.put(i, o)
                break
            }
        }
        prefs.edit().putString("shop_debts", arr.toString()).apply()
    }

    // ---------- Dialogs / records ----------
    private fun companyAddDialog(category: String, existingId: Long? = null) {
        val old = existingId?.let { findById("company", it) }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()) }
        val box = vertical(amount, date)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val v = parseMoney(amount.text.toString())
                if (v <= 0) toast("مبلغ را وارد کنید") else saveEntry("company", Entry(existingId ?: System.currentTimeMillis(), category, v, date.text.toString()))
                showCompany()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun expenseAddDialog(category: String, worker: Boolean = false, existingId: Long? = null) {
        val old = existingId?.let { findById("expense", it) }
        val name = EditText(this).apply { hint = "نام کارگر"; if (!worker) visibility = View.GONE; setText(old?.name ?: "") }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()) }
        val box = vertical(name, amount, date)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val v = parseMoney(amount.text.toString())
                if (v <= 0 || (worker && name.text.toString().trim().isEmpty())) toast("اطلاعات را کامل وارد کنید")
                else saveEntry("expense", Entry(existingId ?: System.currentTimeMillis(), category, v, date.text.toString(), name.text.toString().trim()))
                showExpenses()
            }.setNegativeButton("انصراف", null).show()
    }

    private fun addEntriesForCategory(parent: LinearLayout, store: String, category: String) {
        val arr = readArray(store); var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("category") != category) continue
            count++
            val id = o.optLong("id"); val amount = o.optLong("amount"); val date = o.optString("date"); val name = o.optString("name")
            val title = if (store == "expense" && category == "هزینه کارگر") "$name\n${money(amount)} ریال" else "${money(amount)} ریال"
            parent.addView(recordRow(title, date,
                { if (store == "company") companyAddDialog(category, id) else expenseAddDialog(category, category == "هزینه کارگر", id) },
                { deleteById(store, id); if (store == "company") showCompany() else showExpenses() }), lp(0, 6))
        }
        if (count == 0) parent.addView(empty("ثبت نشده"), lp(0, 3))
    }

    private fun recordRow(title: String, date: String, edit: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 9, 14, 9); background = rounded(white, 14, Color.rgb(226, 231, 241)) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val t = tv(title, 14f, text, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        top.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(smallButton("ویرایش", edit), LinearLayout.LayoutParams(75, 40))
        top.addView(smallButton("حذف", delete), LinearLayout.LayoutParams(60, 40))
        row.addView(top, lp()); row.addView(tv("تاریخ: $date", 12f, gray, Gravity.RIGHT), lp(0, 3))
        return row
    }

    // ---------- Storage ----------
    private fun saveEntry(store: String, entry: Entry) {
        val arr = readArray(store); var replaced = false
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optLong("id") == entry.id) { arr.put(i, entry.toJson()); replaced = true; break }
        }
        if (!replaced) arr.put(entry.toJson())
        prefs.edit().putString(store, arr.toString()).apply()
        toast("ذخیره شد")
    }

    private fun Entry.toJson() = JSONObject().apply { put("id", id); put("category", category); put("amount", amount); put("date", date); put("name", name) }

    private fun findById(store: String, id: Long): Entry? {
        val arr = readArray(store)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("id") == id) return Entry(id, o.optString("category"), o.optLong("amount"), o.optString("date"), o.optString("name"))
        }
        return null
    }

    private fun deleteById(store: String, id: Long) {
        val arr = readArray(store); val out = JSONArray()
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optLong("id") != id) out.put(arr.getJSONObject(i))
        prefs.edit().putString(store, out.toString()).apply()
    }

    private fun readArray(key: String) = JSONArray(prefs.getString(key, "[]") ?: "[]")

    private fun shopDebtTotal(): Long {
        val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) total += arr.getJSONObject(i).optLong("amount")
        return total
    }

    private fun expenseTotal(): Long {
        val arr = readArray("expense"); var total = 0L
        for (i in 0 until arr.length()) total += arr.getJSONObject(i).optLong("amount")
        return total
    }

    private fun sumCategory(category: String): Long {
        val arr = readArray("company"); var total = 0L
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("category") == category) total += arr.getJSONObject(i).optLong("amount")
        return total
    }

    private fun latestAmount(category: String): Long {
        val arr = readArray("company"); var latest = 0L; var latestId = -1L
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("category") == category && o.optLong("id") >= latestId) { latestId = o.optLong("id"); latest = o.optLong("amount") }
        }
        return latest
    }

    // ---------- UI helpers ----------
    private fun baseRoot(title: String, backVisible: Boolean): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val bar = LinearLayout(this).apply { setBackgroundColor(deepBlue); gravity = Gravity.CENTER_VERTICAL; setPadding(10, 10, 10, 10); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        if (backVisible) {
            val back = TextView(this).apply { text = "‹"; textSize = 38f; setTextColor(white); gravity = Gravity.CENTER; setOnClickListener { showDashboard() } }
            bar.addView(back, LinearLayout.LayoutParams(54, 62))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; }
        val t = tv(title, 22f, white, Gravity.CENTER); t.typeface = Typeface.DEFAULT_BOLD
        col.addView(t, lp())
        if (title != "حسابیار لبنیات") col.addView(tv("ثبت و کنترل حساب روزانه", 11f, Color.rgb(220, 226, 245), Gravity.CENTER), lp(0, 2))
        bar.addView(col, LinearLayout.LayoutParams(0, 62, 1f))
        r.addView(bar, lp())
        return r
    }

    private fun actionRow(title: String, click: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 8, 16, 8); background = rounded(white, 16, Color.rgb(220, 226, 240)); setOnClickListener { click() }; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row.addView(tv(title, 16f, text, Gravity.RIGHT), LinearLayout.LayoutParams(0, 52, 1f))
        row.addView(tv("＋", 28f, blue, Gravity.CENTER), LinearLayout.LayoutParams(55, 52))
        return row
    }

    private fun infoBox(title: String, value: String): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 13, 18, 13); background = rounded(Color.rgb(234, 239, 251), 17, Color.rgb(180, 192, 222)) }
        val t = tv(title, 14f, deepBlue, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        b.addView(t, lp()); b.addView(tv(value, 18f, blue, Gravity.RIGHT), lp(0, 4)); return b
    }

    private fun sectionTitle(s: String) = tv(s, 18f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(4, 13, 4, 8) }
    private fun empty(s: String) = tv(s, 12f, gray, Gravity.RIGHT).apply { setPadding(8, 2, 8, 2) }
    private fun tv(s: String, size: Float, color: Int, gravity: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); this.gravity = gravity; layoutDirection = View.LAYOUT_DIRECTION_RTL }
    private fun vertical(vararg views: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10, 4, 10, 0); views.forEach { addView(it, lp(0, 8)) } }
    private fun scroll(content: LinearLayout) = ScrollView(this).apply { setBackgroundColor(bg); addView(content) }
    private fun lp(w: Int = -1, margin: Int = 0) = LinearLayout.LayoutParams(if (w == 0) -1 else w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (margin > 0) setMargins(0, margin, 0, margin) }
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); stroke?.let { setStroke(2, it) } }

    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply { text = label; textSize = 11f; setPadding(2, 0, 2, 0); setOnClickListener { click() } }

    private fun formatMoneyInput(edit: EditText) {
        edit.addTextChangedListener(object : TextWatcher {
            private var busy = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (busy) return
                val raw = e?.toString()?.replace(",", "")?.replace("٬", "")?.replace("ریال", "")?.trim() ?: return
                if (raw.isEmpty()) return
                val n = raw.toLongOrNull() ?: return
                val formatted = money(n)
                if (formatted != e.toString()) { busy = true; edit.setText(formatted); edit.setSelection(formatted.length); busy = false }
            }
        })
    }

    private fun parseMoney(s: String): Long = s.replace(",", "").replace("٬", "").replace("ریال", "").trim().toLongOrNull() ?: 0L
    private fun money(v: Long): String = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US)).format(v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // Gregorian -> Jalali conversion, no external library required.
    private fun todayJalali(): String {
        val c = Calendar.getInstance()
        val j = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return "%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2])
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0,31,28,31,30,31,30,31,31,30,31,30,31)
        val jdm = intArrayOf(0,31,31,31,31,31,31,30,30,30,30,30,29)
        var y = gy - 1600; var m = gm - 1; var d = gd - 1
        var days = 365*y + (y+3)/4 - (y+99)/100 + (y+399)/400
        for (i in 0 until m) days += gdm[i+1]
        if (m > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) days++
        days += d
        var jdays = days - 79
        val jy = 979 + 33*(jdays/12053)
        jdays %= 12053
        var jyy = jy + 4*(jdays/1461)
        jdays %= 1461
        if (jdays >= 366) { jyy += (jdays-1)/365; jdays = (jdays-1)%365 }
        var jm = 0; while (jm < 11 && jdays >= jdm[jm+1]) { jdays -= jdm[jm+1]; jm++ }
        return intArrayOf(jyy, jm+1, jdays+1)
    }
}
