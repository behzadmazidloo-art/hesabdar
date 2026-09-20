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
    data class Period(val from: String, val to: String, val title: String)

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
            setPadding(16, 16, 16, 16)
        }
        grid.addView(squareCard("حساب شرکت", "بارنامه و تسویه شرکت", "▣", { showCompany() }), lp(0, 9))
        grid.addView(squareCard("هزینه ها", "خودرو، کارگر و بیمه", "▤", { showExpenses() }), lp(0, 9))
        grid.addView(squareCard("بدهی مغازه دارها", "بدهی، تسویه و مانده فروشگاه ها", "▥", { showShopDebts() }), lp(0, 9))
        grid.addView(squareCard("گزارش گیری", "گزارش روزانه، ماهانه و تراز کامل", "▤", { showReports() }), lp(0, 9))
        r.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(tv("تمام مبالغ به ریال • تاریخ ها شمسی • اطلاعات ذخیره می شود", 12f, gray, Gravity.CENTER), lp(0, 8))
        setContentView(scroll(r))
    }

    private fun squareCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 94
            setPadding(18, 15, 18, 15)
            background = rounded(white, 22, Color.rgb(218, 225, 242))
            elevation = 7f
            setOnClickListener { click() }
        }
        val iconBox = TextView(this).apply {
            text = icon; textSize = 30f; setTextColor(white); gravity = Gravity.CENTER
            background = rounded(blue, 18, null)
        }
        box.addView(iconBox, LinearLayout.LayoutParams(70, 70))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(15, 0, 0, 0) }
        val t = tv(title, 19f, deepBlue, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        col.addView(t, lp())
        col.addView(tv(subtitle, 12.5f, gray, Gravity.RIGHT), lp(0, 5))
        box.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        box.addView(tv("‹", 34f, blue, Gravity.CENTER), LinearLayout.LayoutParams(42, -1))
        return box
    }

    // ---------- Company ----------
    private fun showCompany() {
        val r = baseRoot("حساب شرکت", true)
        r.addView(sectionTitle("ثبت روزانه حساب شرکت"), lp())
        r.addView(infoBox("بدهی مغازه دارها (خودکار)", "${money(shopDebtTotal())} ریال"), lp(0, 5))

        val cats = listOf("مبلغ بارنامه", "ضایعات", "کارتخوان", "پول نقد", "تخفیفات", "برگشتی")
        cats.forEach { c ->
            r.addView(actionRow("ثبت $c") { companyAddDialog(c) }, lp(0, 6))
            addEntriesForCategory(r, "company", c)
        }

        r.addView(sectionTitle("خلاصه حساب شرکت"), lp(0, 12))
        r.addView(companySummary(), lp(0, 6))
        r.addView(actionRow("ثبت و مشاهده گزارش امروز") { showReportsFor(Period(todayJalali(), todayJalali(), "امروز")) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 16))
        setContentView(scroll(r))
    }

    private fun companySummary(): LinearLayout {
        val barnameh = latestAmount("مبلغ بارنامه")
        val waste = sumCategory("ضایعات")
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
        addSummaryLine(b, label, amount, if (diff < 0) red else green)
        addSummaryLine(b, "سود پایه ۵.۷٪ پس از کسر ضایعات", profit, green)
        return b
    }

    private fun addSummaryLine(parent: LinearLayout, label: String, amount: Long, color: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 6, 0, 6); layoutDirection = View.LAYOUT_DIRECTION_RTL; minimumHeight = 36 }
        row.addView(tv(label, 14f, gray, Gravity.RIGHT), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv("${money(amount)} ریال", 15f, color, Gravity.LEFT), LinearLayout.LayoutParams(-2, -2))
        parent.addView(row)
    }

    // ---------- Expenses ----------
    private fun showExpenses() {
        val r = baseRoot("هزینه ها", true)
        r.addView(sectionTitle("هزینه های خودرو"), lp())
        listOf("گازوئیل", "تعمیرات", "خرید قطعات", "تعمیر یخچال").forEach { c ->
            r.addView(actionRow("ثبت $c") { expenseAddDialog(c) }, lp(0, 6))
            addEntriesForCategory(r, "expense", c)
        }
        r.addView(sectionTitle("هزینه کارگر"), lp(0, 10))
        r.addView(actionRow("ثبت نام کارگر و مبلغ") { expenseAddDialog("هزینه کارگر", true) }, lp(0, 6))
        addEntriesForCategory(r, "expense", "هزینه کارگر")
        r.addView(sectionTitle("هزینه بیمه"), lp(0, 10))
        r.addView(actionRow("ثبت هزینه بیمه") { expenseAddDialog("هزینه بیمه") }, lp(0, 6))
        addEntriesForCategory(r, "expense", "هزینه بیمه")
        r.addView(infoBox("جمع کل هزینه ها", "${money(expenseTotal())} ریال"), lp(0, 12))
        r.addView(actionRow("ثبت و مشاهده گزارش امروز") { showReportsFor(Period(todayJalali(), todayJalali(), "امروز")) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 16))
        setContentView(scroll(r))
    }

    // ---------- Shop debts ----------
    private fun showShopDebts() {
        val r = baseRoot("بدهی مغازه دارها", true)
        r.addView(sectionTitle("ثبت نام فروشگاه، مبلغ بدهی و تسویه"), lp())
        r.addView(infoBox("جمع بدهی باقی مانده", "${money(shopDebtTotal())} ریال"), lp(0, 5))
        r.addView(actionRow("ثبت فروشگاه جدید") { shopDebtDialog(null) }, lp(0, 7))

        val arr = readArray("shop_debts")
        if (arr.length() == 0) r.addView(empty("هنوز فروشگاهی ثبت نشده است."), lp(0, 14))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("id")
            val name = o.optString("name")
            val amount = o.optLong("amount")
            val date = o.optString("date")
            val status = if (amount == 0L) "تسویه شده" else "مانده بدهی: ${money(amount)} ریال"
            val row = shopRow(name, status, date, amount,
                { shopDebtDialog(id) },
                { if (amount > 0) settlementDialog(id) else toast("این حساب قبلاً تسویه شده است") },
                { deleteById("shop_debts", id); showShopDebts() })
            r.addView(row, lp(0, 9))
        }
        r.addView(actionRow("ثبت و مشاهده گزارش امروز") { showReportsFor(Period(todayJalali(), todayJalali(), "امروز")) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 16))
        setContentView(scroll(r))
    }

    private fun shopRow(name: String, status: String, date: String, amount: Long, edit: () -> Unit, settle: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(15, 13, 15, 13); background = rounded(white, 18, Color.rgb(220, 226, 240)); elevation = 2f }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val title = tv(name, 17f, text, Gravity.RIGHT); title.typeface = Typeface.DEFAULT_BOLD
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(smallButton("ویرایش حساب", edit), LinearLayout.LayoutParams(108, 44))
        row.addView(top, lp())
        row.addView(tv(status, 15f, if (amount == 0L) green else red, Gravity.RIGHT), lp(0, 6))
        row.addView(tv("تاریخ ثبت: $date", 12f, gray, Gravity.RIGHT), lp(0, 3))
        if (amount > 0) row.addView(actionRow("ثبت مبلغ تسویه شده") { settle() }, lp(0, 7))
        row.addView(smallButton("حذف حساب", delete), LinearLayout.LayoutParams(-1, 42).apply { topMargin = 6 })
        return row
    }

    private fun shopDebtDialog(existingId: Long?) {
        val old = existingId?.let { findById("shop_debts", it) }
        val name = EditText(this).apply { hint = "نام فروشگاه / مغازه دار"; setText(old?.name ?: ""); textSize = 16f }
        val amount = EditText(this).apply { hint = "مبلغ بدهی به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 16f; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()); textSize = 16f }
        val box = vertical(name, amount, date)
        AlertDialog.Builder(this)
            .setTitle(if (old == null) "ثبت فروشگاه" else "ویرایش حساب فروشگاه")
            .setView(box)
            .setPositiveButton("ثبت", null)
            .setNegativeButton("انصراف", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val n = name.text.toString().trim(); val v = parseMoney(amount.text.toString())
                        if (n.isEmpty() || v < 0) toast("نام فروشگاه و مبلغ را درست وارد کنید")
                        else { saveEntry("shop_debts", Entry(existingId ?: System.currentTimeMillis(), "", v, date.text.toString(), n)); dialog.dismiss(); showShopDebts() }
                    }
                }
            }.show()
    }

    private fun settlementDialog(id: Long) {
        val old = findById("shop_debts", id) ?: return
        val amount = EditText(this).apply { hint = "مبلغی که فروشگاه تسویه کرده"; inputType = InputType.TYPE_CLASS_NUMBER; textSize = 16f; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ تسویه شمسی"; setText(todayJalali()); textSize = 16f }
        val box = vertical(amount, date)
        AlertDialog.Builder(this)
            .setTitle("ثبت تسویه ${old.name}")
            .setMessage("مانده فعلی: ${money(old.amount)} ریال")
            .setView(box)
            .setPositiveButton("ثبت تسویه", null)
            .setNegativeButton("انصراف", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val payment = parseMoney(amount.text.toString())
                        when {
                            payment <= 0 -> toast("مبلغ تسویه را وارد کنید")
                            payment > old.amount -> toast("مبلغ تسویه نمی تواند بیشتر از بدهی باشد")
                            else -> { val newBalance = old.amount - payment; updateShopDebt(id, newBalance, date.text.toString(), payment); toast("تسویه ثبت شد"); dialog.dismiss(); showShopDebts() }
                        }
                    }
                }
            }.show()
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

    // ---------- Entry dialogs ----------
    private fun companyAddDialog(category: String, existingId: Long? = null) {
        val old = existingId?.let { findById("company", it) }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 16f; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()); textSize = 16f }
        val box = vertical(amount, date)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val v = parseMoney(amount.text.toString())
                        if (v <= 0) toast("مبلغ را وارد کنید") else { saveEntry("company", Entry(existingId ?: System.currentTimeMillis(), category, v, date.text.toString())); dialog.dismiss(); showCompany() }
                    }
                }
            }.show()
    }

    private fun expenseAddDialog(category: String, worker: Boolean = false, existingId: Long? = null) {
        val old = existingId?.let { findById("expense", it) }
        val name = EditText(this).apply { hint = "نام کارگر"; if (!worker) visibility = View.GONE; setText(old?.name ?: ""); textSize = 16f }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 16f; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()); textSize = 16f }
        val box = vertical(name, amount, date)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val v = parseMoney(amount.text.toString())
                        if (v <= 0 || (worker && name.text.toString().trim().isEmpty())) toast("اطلاعات را کامل وارد کنید")
                        else { saveEntry("expense", Entry(existingId ?: System.currentTimeMillis(), category, v, date.text.toString(), name.text.toString().trim())); dialog.dismiss(); showExpenses() }
                    }
                }
            }.show()
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
                { deleteById(store, id); if (store == "company") showCompany() else showExpenses() }), lp(0, 5))
        }
        if (count == 0) parent.addView(empty("هنوز ثبت نشده"), lp(0, 2))
    }

    private fun recordRow(title: String, date: String, edit: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 10); background = rounded(white, 14, Color.rgb(226, 231, 241)); minimumHeight = 70 }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val t = tv(title, 14f, text, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        top.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(smallButton("ویرایش", edit), LinearLayout.LayoutParams(100, 42))
        top.addView(smallButton("حذف", delete), LinearLayout.LayoutParams(72, 42))
        row.addView(top, lp()); row.addView(tv("تاریخ: $date", 12f, gray, Gravity.RIGHT), lp(0, 4))
        return row
    }

    // ---------- Reports ----------
    private fun showReports() {
        showReportsFor(Period(todayJalali(), todayJalali(), "امروز"))
    }

    private fun showReportsFor(initial: Period) {
        val r = baseRoot("گزارش گیری", true)
        r.addView(sectionTitle("گزارش جزئی یا تراز کلی"), lp())
        r.addView(infoBox("بازه گزارش", "${initial.title}: ${initial.from} تا ${initial.to}"), lp(0, 4))
        r.addView(actionRow("انتخاب بازه گزارش") { periodDialog { p -> showReportsFor(p) } }, lp(0, 7))

        val categories = linkedMapOf(
            "گزارش کلی / تراز" to "__ALL__",
            "مبلغ بارنامه" to "مبلغ بارنامه",
            "ضایعات" to "ضایعات",
            "کارتخوان" to "کارتخوان",
            "پول نقد" to "پول نقد",
            "تخفیفات" to "تخفیفات",
            "برگشتی" to "برگشتی",
            "بدهی مغازه دارها" to "__DEBTS__",
            "گازوئیل" to "گازوئیل",
            "تعمیرات" to "تعمیرات",
            "خرید قطعات" to "خرید قطعات",
            "تعمیر یخچال" to "تعمیر یخچال",
            "هزینه کارگر" to "هزینه کارگر",
            "هزینه بیمه" to "هزینه بیمه",
            "تسویه های مغازه دارها" to "__PAYMENTS__"
        )
        categories.forEach { (label, key) ->
            r.addView(actionRow("گزارش $label") { showSingleReport(initial, label, key) }, lp(0, 5))
        }
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 16))
        setContentView(scroll(r))
    }

    private fun showSingleReport(period: Period, label: String, key: String) {
        val r = baseRoot(label, true)
        r.addView(infoBox("بازه گزارش", "${period.title}: ${period.from} تا ${period.to}"), lp(0, 5))
        if (key == "__ALL__") {
            r.addView(reportOverall(period), lp(0, 8))
        } else if (key == "__DEBTS__") {
            r.addView(reportDebts(period), lp(0, 8))
        } else if (key == "__PAYMENTS__") {
            r.addView(reportPayments(period), lp(0, 8))
        } else {
            val store = if (listOf("گازوئیل", "تعمیرات", "خرید قطعات", "تعمیر یخچال", "هزینه کارگر", "هزینه بیمه").contains(key)) "expense" else "company"
            val arr = readArray(store)
            var total = 0L; var count = 0
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 14, 16, 14); background = rounded(white, 18, Color.rgb(215, 222, 240)) }
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optString("category") == key && inPeriod(o.optString("date"), period)) {
                    val amount = o.optLong("amount"); total += amount; count++
                    val name = o.optString("name")
                    val title = if (key == "هزینه کارگر" && name.isNotBlank()) "$name — ${money(amount)} ریال" else "${money(amount)} ریال"
                    addSummaryLine(box, title, amount, text)
                    box.addView(tv("تاریخ: ${o.optString("date")}", 12f, gray, Gravity.RIGHT), lp(0, 1))
                }
            }
            addSummaryLine(box, "تعداد ثبت ها", count.toLong(), blue)
            addSummaryLine(box, "جمع $label", total, green)
            if (count == 0) box.addView(empty("در این بازه ثبت نشده است."), lp(0, 5))
            r.addView(box, lp(0, 8))
        }
        r.addView(actionRow("تغییر بازه گزارش") { periodDialog { p -> showSingleReport(p, label, key) } }, lp(0, 8))
        r.addView(actionRow("بازگشت به گزارش ها") { showReportsFor(period) }, lp(0, 8))
        setContentView(scroll(r))
    }

    private fun reportOverall(period: Period): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = rounded(white, 18, Color.rgb(215, 222, 240)) }
        val barnameh = sumCategoryInPeriod("مبلغ بارنامه", period)
        val waste = sumCategoryInPeriod("ضایعات", period)
        val card = sumCategoryInPeriod("کارتخوان", period)
        val cash = sumCategoryInPeriod("پول نقد", period)
        val discount = sumCategoryInPeriod("تخفیفات", period)
        val returns = sumCategoryInPeriod("برگشتی", period)
        val shopDebts = shopDebtBalanceAtPeriod(period)
        val settlement = waste + card + cash + discount + returns + shopDebts
        val diff = settlement - barnameh
        val baseProfit = ((barnameh - waste).coerceAtLeast(0L) * 57L) / 1000L
        val expenses = expenseTotalInPeriod(period)
        val extra = if (diff > 0) diff else 0L
        val companyDebt = if (diff < 0) -diff else 0L
        val net = baseProfit + extra - expenses

        addSummaryLine(b, "مبلغ بارنامه", barnameh, deepBlue)
        addSummaryLine(b, "ضایعات", waste, text)
        addSummaryLine(b, "کارتخوان", card, text)
        addSummaryLine(b, "پول نقد", cash, text)
        addSummaryLine(b, "بدهی مغازه دارها", shopDebts, text)
        addSummaryLine(b, "تخفیفات", discount, text)
        addSummaryLine(b, "برگشتی", returns, text)
        addSummaryLine(b, "جمع تسویه", settlement, blue)
        addSummaryLine(b, if (diff > 0) "سود اضافه حساب" else if (diff < 0) "بدهی باقی مانده" else "تسویه کامل", kotlin.math.abs(diff), if (diff < 0) red else green)
        addSummaryLine(b, "سود پایه ۵.۷٪", baseProfit, green)
        addSummaryLine(b, "هزینه ها", expenses, red)
        addSummaryLine(b, "سود خالص تقریبی", net, if (net >= 0) green else red)
        addSummaryLine(b, "جمع بدهی فعلی مغازه دارها", shopDebtTotal(), text)
        addSummaryLine(b, "سود اضافه حساب", extra, green)
        addSummaryLine(b, "بدهی باقی مانده", companyDebt, red)
        return b
    }

    private fun reportDebts(period: Period): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = rounded(white, 18, Color.rgb(215, 222, 240)) }
        val arr = readArray("shop_debts")
        var total = 0L
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val amount = o.optLong("amount")
            total += amount
            addSummaryLine(b, o.optString("name"), amount, if (amount == 0L) green else red)
            b.addView(tv("ثبت اولیه: ${o.optString("date")}", 12f, gray, Gravity.RIGHT), lp(0, 1))
        }
        addSummaryLine(b, "جمع مانده بدهی", total, blue)
        if (arr.length() == 0) b.addView(empty("هیچ بدهکاری ثبت نشده است."), lp(0, 5))
        b.addView(tv("تسویه های ثبت شده در بازه", 16f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, lp(0, 10))
        val payments = reportPaymentsData(period)
        payments.forEach { p ->
            addSummaryLine(b, p.first, p.second, green)
            b.addView(tv("تاریخ تسویه: ${p.third}", 12f, gray, Gravity.RIGHT), lp(0, 1))
        }
        return b
    }

    private fun reportPayments(period: Period): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 15, 16, 15); background = rounded(white, 18, Color.rgb(215, 222, 240)) }
        val payments = reportPaymentsData(period)
        var total = 0L
        payments.forEach { p ->
            total += p.second
            addSummaryLine(b, p.first, p.second, green)
            b.addView(tv("تاریخ تسویه: ${p.third}", 12f, gray, Gravity.RIGHT), lp(0, 1))
        }
        addSummaryLine(b, "جمع تسویه های بازه", total, blue)
        if (payments.isEmpty()) b.addView(empty("در این بازه تسویه ای ثبت نشده است."), lp(0, 5))
        return b
    }

    private fun reportPaymentsData(period: Period): List<Triple<String, Long, String>> {
        val out = mutableListOf<Triple<String, Long, String>>()
        val arr = readArray("shop_debts")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val name = o.optString("name")
            val p = o.optJSONArray("payments") ?: JSONArray()
            for (j in 0 until p.length()) {
                val po = p.getJSONObject(j); val date = po.optString("date")
                if (inPeriod(date, period)) out.add(Triple(name, po.optLong("amount"), date))
            }
        }
        return out
    }

    private fun periodDialog(onDone: (Period) -> Unit) {
        val options = arrayOf("امروز", "این ماه", "بازه دلخواه")
        AlertDialog.Builder(this).setTitle("انتخاب بازه گزارش").setItems(options) { _, which ->
            when (which) {
                0 -> onDone(Period(todayJalali(), todayJalali(), "امروز"))
                1 -> { val t = todayJalali(); val month = t.substring(0, 7); onDone(Period("$month/01", "$month/31", "این ماه")) }
                2 -> customPeriodDialog(onDone)
            }
        }.show()
    }

    private fun customPeriodDialog(onDone: (Period) -> Unit) {
        val from = EditText(this).apply { hint = "از تاریخ 1405/01/01"; textSize = 16f }
        val to = EditText(this).apply { hint = "تا تاریخ 1405/01/31"; textSize = 16f }
        val box = vertical(from, to)
        AlertDialog.Builder(this).setTitle("بازه دلخواه شمسی").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val f = from.text.toString().trim(); val t = to.text.toString().trim()
                        if (!validDate(f) || !validDate(t) || f > t) toast("تاریخ ها را به شکل 1405/01/01 درست وارد کنید")
                        else { dialog.dismiss(); onDone(Period(f, t, "بازه دلخواه")) }
                    }
                }
            }.show()
    }

    private fun validDate(s: String): Boolean = Regex("\\d{4}/\\d{2}/\\d{2}").matches(s)
    private fun inPeriod(date: String, period: Period): Boolean = validDate(date) && date >= period.from && date <= period.to

    // ---------- Storage ----------
    private fun saveEntry(store: String, entry: Entry) {
        val arr = readArray(store); var replaced = false
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optLong("id") == entry.id) { arr.put(i, entry.toJson()); replaced = true; break }
        }
        if (!replaced) arr.put(entry.toJson())
        prefs.edit().putString(store, arr.toString()).apply()
        toast("ثبت شد")
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

    private fun expenseTotalInPeriod(period: Period): Long {
        val arr = readArray("expense"); var total = 0L
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (inPeriod(o.optString("date"), period)) total += o.optLong("amount") }
        return total
    }

    private fun sumCategory(category: String): Long {
        val arr = readArray("company"); var total = 0L
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optString("category") == category) total += arr.getJSONObject(i).optLong("amount")
        return total
    }

    private fun sumCategoryInPeriod(category: String, period: Period): Long {
        val arr = readArray("company"); var total = 0L
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("category") == category && inPeriod(o.optString("date"), period)) total += o.optLong("amount") }
        return total
    }

    private fun shopDebtBalanceAtPeriod(period: Period): Long {
        // The app keeps the current balance. For a historical period, report the current balance
        // and separately shows payments made during that period, so no history is invented.
        return shopDebtTotal()
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
        val bar = LinearLayout(this).apply { setBackgroundColor(deepBlue); gravity = Gravity.CENTER_VERTICAL; setPadding(8, 8, 8, 8); layoutDirection = View.LAYOUT_DIRECTION_RTL; minimumHeight = 72 }
        if (backVisible) {
            val back = TextView(this).apply { text = "‹"; textSize = 38f; setTextColor(white); gravity = Gravity.CENTER; setOnClickListener { showDashboard() } }
            bar.addView(back, LinearLayout.LayoutParams(54, 68))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; }
        val t = tv(title, 22f, white, Gravity.CENTER); t.typeface = Typeface.DEFAULT_BOLD
        col.addView(t, lp())
        if (title != "حسابیار لبنیات") col.addView(tv("ثبت و کنترل حساب روزانه", 11f, Color.rgb(220, 226, 245), Gravity.CENTER), lp(0, 2))
        bar.addView(col, LinearLayout.LayoutParams(0, 68, 1f))
        r.addView(bar, lp())
        return r
    }

    private fun actionRow(title: String, click: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 7, 16, 7); minimumHeight = 64; background = rounded(white, 16, Color.rgb(220, 226, 240)); setOnClickListener { click() }; layoutDirection = View.LAYOUT_DIRECTION_RTL }
        row.addView(tv(title, 16f, text, Gravity.RIGHT), LinearLayout.LayoutParams(0, 56, 1f))
        row.addView(tv("＋", 28f, blue, Gravity.CENTER), LinearLayout.LayoutParams(55, 56))
        return row
    }

    private fun infoBox(title: String, value: String): LinearLayout {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 14, 18, 14); background = rounded(Color.rgb(234, 239, 251), 17, Color.rgb(180, 192, 222)); minimumHeight = 76 }
        val t = tv(title, 14f, deepBlue, Gravity.RIGHT); t.typeface = Typeface.DEFAULT_BOLD
        b.addView(t, lp()); b.addView(tv(value, 18f, blue, Gravity.RIGHT), lp(0, 5)); return b
    }

    private fun sectionTitle(s: String) = tv(s, 18f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(4, 14, 4, 9); minimumHeight = 46 }
    private fun empty(s: String) = tv(s, 13f, gray, Gravity.RIGHT).apply { setPadding(8, 4, 8, 4); minimumHeight = 34 }
    private fun tv(s: String, size: Float, color: Int, gravity: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); this.gravity = gravity; layoutDirection = View.LAYOUT_DIRECTION_RTL; includeFontPadding = true; setLineSpacing(2f, 1.05f) }
    private fun vertical(vararg views: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10, 6, 10, 2); views.forEach { addView(it, lp(0, 10)) } }
    private fun scroll(content: LinearLayout) = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; addView(content) }
    private fun lp(w: Int = -1, margin: Int = 0) = LinearLayout.LayoutParams(if (w == 0) -1 else w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (margin > 0) setMargins(0, margin, 0, margin) }
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); stroke?.let { setStroke(2, it) } }

    private fun smallButton(label: String, click: () -> Unit) = Button(this).apply { text = label; textSize = 11.5f; minWidth = 0; minHeight = 0; setPadding(3, 0, 3, 0); isAllCaps = false; setOnClickListener { click() } }

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
