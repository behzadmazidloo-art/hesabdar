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
    private val blue = Color.rgb(28, 76, 178)
    private val blue2 = Color.rgb(47, 96, 196)
    private val deepBlue = Color.rgb(13, 46, 118)
    private val bg = Color.rgb(244, 247, 252)
    private val text = Color.rgb(38, 43, 54)
    private val gray = Color.rgb(105, 111, 124)
    private val green = Color.rgb(30, 137, 88)
    private val red = Color.rgb(194, 58, 69)
    private val white = Color.WHITE

    data class Entry(val id: Long, val category: String, val amount: Long, val date: String, val name: String = "")
    data class Period(val from: String, val to: String, val title: String)

    private var companyDate = todayJalali()
    private var expenseDate = todayJalali()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        migrateDebtHistory()
        showDashboard()
    }

    // ---------- Dashboard ----------
    private fun showDashboard() {
        val r = baseRoot("حسابیار لبنیات", false)
        r.addView(tv("مدیریت حساب و تسویه روزانه", 16f, gray, Gravity.CENTER), lp(0, 10))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 14, 14, 14) }
        grid.addView(squareCard("حساب شرکت", "بارنامه و تسویه روزانه", "▣") { showCompany(companyDate) }, lp(0, 10))
        grid.addView(squareCard("هزینه ها", "خودرو، کارگر و بیمه", "▤") { showExpenses(expenseDate) }, lp(0, 10))
        grid.addView(squareCard("بدهی مغازه دارها", "بدهی، وصولی و مانده فروشگاه ها", "▥") { showShopDebts() }, lp(0, 10))
        grid.addView(squareCard("گزارش گیری", "گزارش جزئی و تراز کامل", "▤") { showReports() }, lp(0, 10))
        r.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(tv("تمام مبالغ به ریال • تاریخ شمسی و روز هفته • اطلاعات ذخیره می شود", 12f, gray, Gravity.CENTER), lp(0, 10))
        setContentView(scroll(r))
    }

    private fun squareCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 112; setPadding(18, 16, 18, 16)
            background = rounded(white, 24, Color.rgb(211, 221, 242)); elevation = 7f
            setOnClickListener { click() }; layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        box.addView(TextView(this).apply { text = icon; textSize = 31f; setTextColor(white); gravity = Gravity.CENTER; background = rounded(blue, 20, null) }, LinearLayout.LayoutParams(76, 76))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(16, 0, 0, 0) }
        col.addView(tv(title, 20f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, lp())
        col.addView(tv(subtitle, 13.5f, gray, Gravity.RIGHT), lp(0, 7))
        box.addView(col, LinearLayout.LayoutParams(0, -1, 1f))
        box.addView(tv("‹", 36f, blue, Gravity.CENTER), LinearLayout.LayoutParams(44, -1))
        return box
    }

    // ---------- Company ----------
    private fun showCompany(date: String = companyDate) {
        companyDate = if (validDate(date)) date else todayJalali()
        val r = baseRoot("حساب شرکت", true)
        r.addView(sectionTitle("ثبت و کنترل حساب شرکت"), lp())
        r.addView(infoBox("تاریخ حساب", displayDate(companyDate)), lp(0, 4))
        r.addView(actionRow("تغییر تاریخ حساب") { dateDialog(companyDate) { showCompany(it) } }, lp(0, 7))

        val newDebt = shopDebtNewOnDate(companyDate)
        val collection = shopDebtCollectionsOnDate(companyDate)
        if (newDebt > 0) r.addView(infoBox("بدهی جدید امروز", "${money(newDebt)} ریال"), lp(0, 5))
        if (collection > 0) r.addView(infoBox("وصولی طلب قبلی امروز", "${money(collection)} ریال"), lp(0, 5))

        val cats = listOf("مبلغ بارنامه", "ضایعات", "کارتخوان", "پول نقد", "تخفیفات", "برگشتی")
        cats.forEach { c ->
            r.addView(actionRow("ثبت $c") { companyAddDialog(c, null, companyDate) }, lp(0, 6))
            addEntriesForCategory(r, "company", c, companyDate)
        }
        r.addView(sectionTitle("خلاصه حساب امروز"), lp(0, 14))
        r.addView(companySummary(companyDate), lp(0, 6))
        r.addView(actionRow("ثبت و مشاهده گزارش این روز") { showReportsFor(Period(companyDate, companyDate, displayDate(companyDate))) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    private fun companySummary(date: String): LinearLayout {
        val barnameh = sumCategoryOnDate("مبلغ بارنامه", date)
        val waste = sumCategoryOnDate("ضایعات", date)
        val card = sumCategoryOnDate("کارتخوان", date)
        val cash = sumCategoryOnDate("پول نقد", date)
        val discount = sumCategoryOnDate("تخفیفات", date)
        val returns = sumCategoryOnDate("برگشتی", date)
        val addedDebt = shopDebtNewOnDate(date)
        val collectedOldDebt = shopDebtCollectionsOnDate(date)
        val adjustedBarnameh = barnameh + collectedOldDebt
        val settlement = waste + card + cash + discount + returns + addedDebt
        val diff = settlement - adjustedBarnameh
        val profit = ((adjustedBarnameh - waste).coerceAtLeast(0L) * 57L) / 1000L

        val b = cardBox()
        addSummaryLine(b, "مبلغ بارنامه امروز", barnameh, deepBlue)
        if (collectedOldDebt > 0) addSummaryLine(b, "افزایش بارنامه بابت وصولی طلب قبلی", collectedOldDebt, blue)
        addSummaryLine(b, "بارنامه مبنا", adjustedBarnameh, deepBlue)
        addSummaryLine(b, "ضایعات", waste, text)
        addSummaryLine(b, "کارتخوان", card, text)
        addSummaryLine(b, "پول نقد", cash, text)
        addSummaryLine(b, "بدهی جدید امروز", addedDebt, text)
        addSummaryLine(b, "تخفیفات", discount, text)
        addSummaryLine(b, "برگشتی", returns, text)
        addSummaryLine(b, "جمع تسویه امروز", settlement, blue)
        val label = when { diff > 0 -> "سود اضافه حساب"; diff < 0 -> "بدهی باقی مانده"; else -> "تسویه کامل" }
        addSummaryLine(b, label, kotlin.math.abs(diff), if (diff < 0) red else green)
        addSummaryLine(b, "سود پایه ۵.۷٪ پس از کسر ضایعات", profit, green)
        return b
    }

    // ---------- Expenses ----------
    private fun showExpenses(date: String = expenseDate) {
        expenseDate = if (validDate(date)) date else todayJalali()
        val r = baseRoot("هزینه ها", true)
        r.addView(sectionTitle("ثبت هزینه های روزانه"), lp())
        r.addView(infoBox("تاریخ هزینه ها", displayDate(expenseDate)), lp(0, 4))
        r.addView(actionRow("تغییر تاریخ هزینه ها") { dateDialog(expenseDate) { showExpenses(it) } }, lp(0, 7))
        r.addView(sectionTitle("هزینه های خودرو"), lp(0, 10))
        listOf("گازوئیل", "تعمیرات", "خرید قطعات", "تعمیر یخچال").forEach { c ->
            r.addView(actionRow("ثبت $c") { expenseAddDialog(c, false, null, expenseDate) }, lp(0, 6))
            addEntriesForCategory(r, "expense", c, expenseDate)
        }
        r.addView(sectionTitle("هزینه کارگر"), lp(0, 12))
        r.addView(actionRow("ثبت نام کارگر و مبلغ") { expenseAddDialog("هزینه کارگر", true, null, expenseDate) }, lp(0, 6))
        addEntriesForCategory(r, "expense", "هزینه کارگر", expenseDate)
        r.addView(sectionTitle("هزینه بیمه"), lp(0, 12))
        r.addView(actionRow("ثبت هزینه بیمه") { expenseAddDialog("هزینه بیمه", false, null, expenseDate) }, lp(0, 6))
        addEntriesForCategory(r, "expense", "هزینه بیمه", expenseDate)
        r.addView(infoBox("جمع هزینه های این روز", "${money(expenseTotalOnDate(expenseDate))} ریال"), lp(0, 12))
        r.addView(actionRow("ثبت و مشاهده گزارش این روز") { showReportsFor(Period(expenseDate, expenseDate, displayDate(expenseDate))) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    // ---------- Shop debts ----------
    private fun showShopDebts() {
        val r = baseRoot("بدهی مغازه دارها", true)
        r.addView(sectionTitle("ثبت فروشگاه، بدهی و وصولی"), lp())
        r.addView(infoBox("جمع مانده بدهی همه فروشگاه ها", "${money(shopDebtTotal())} ریال"), lp(0, 5))
        r.addView(actionRow("ثبت فروشگاه جدید") { shopDebtDialog(null) }, lp(0, 8))
        val arr = readArray("shop_debts")
        if (arr.length() == 0) r.addView(empty("هنوز فروشگاهی ثبت نشده است."), lp(0, 14))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); val id = o.optLong("id"); val name = o.optString("name"); val amount = o.optLong("amount"); val date = o.optString("date")
            val row = shopRow(name, amount, date,
                { shopDebtDialog(id) },
                { if (amount > 0) settlementDialog(id) else toast("این حساب قبلاً تسویه شده است") },
                { deleteById("shop_debts", id); showShopDebts() })
            r.addView(row, lp(0, 9))
        }
        r.addView(actionRow("ثبت و مشاهده گزارش امروز") { showReportsFor(Period(todayJalali(), todayJalali(), "امروز")) }, lp(0, 10))
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    private fun shopRow(name: String, amount: Long, date: String, edit: () -> Unit, settle: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = cardBox()
        row.addView(tv(name, 19f, text, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, lp())
        row.addView(tv(if (amount == 0L) "تسویه شده" else "مانده بدهی: ${money(amount)} ریال", 16f, if (amount == 0L) green else red, Gravity.RIGHT), lp(0, 8))
        row.addView(tv("آخرین تاریخ ثبت: ${displayDate(date)}", 13f, gray, Gravity.RIGHT), lp(0, 5))
        row.addView(actionButtonRow("ویرایش حساب فروشگاه", edit), lp(0, 8))
        if (amount > 0) row.addView(actionButtonRow("ثبت مبلغ تسویه شده", settle), lp(0, 7))
        row.addView(actionButtonRow("حذف حساب", delete), lp(0, 7))
        return row
    }

    private fun shopDebtDialog(existingId: Long?) {
        val old = existingId?.let { findById("shop_debts", it) }
        val name = EditText(this).apply { hint = "نام فروشگاه / مغازه دار"; setText(old?.name ?: ""); textSize = 18f; minHeight = 58 }
        val amount = EditText(this).apply { hint = "مبلغ بدهی به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 18f; minHeight = 58; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: todayJalali()); textSize = 18f; minHeight = 58 }
        val day = tv(displayDate(old?.date ?: todayJalali()), 14f, blue, Gravity.RIGHT)
        date.addTextChangedListener(simpleDateWatcher(date, day))
        val box = vertical(name, amount, date, day)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت فروشگاه" else "ویرایش حساب فروشگاه").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val n = name.text.toString().trim(); val v = parseMoney(amount.text.toString()); val d = date.text.toString().trim()
                        if (n.isEmpty() || v < 0 || !validDate(d)) toast("نام، مبلغ و تاریخ را درست وارد کنید")
                        else { saveShopDebt(existingId, n, v, d); dialog.dismiss(); showShopDebts() }
                    }
                }
            }.show()
    }

    private fun settlementDialog(id: Long) {
        val old = findById("shop_debts", id) ?: return
        val amount = EditText(this).apply { hint = "مبلغی که فروشگاه تسویه کرده"; inputType = InputType.TYPE_CLASS_NUMBER; textSize = 18f; minHeight = 58; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ تسویه شمسی"; setText(todayJalali()); textSize = 18f; minHeight = 58 }
        val day = tv(displayDate(todayJalali()), 14f, blue, Gravity.RIGHT)
        date.addTextChangedListener(simpleDateWatcher(date, day))
        val box = vertical(amount, date, day)
        AlertDialog.Builder(this).setTitle("ثبت تسویه ${old.name}").setMessage("مانده فعلی: ${money(old.amount)} ریال").setView(box)
            .setPositiveButton("ثبت تسویه", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val payment = parseMoney(amount.text.toString()); val d = date.text.toString().trim()
                        when {
                            payment <= 0 -> toast("مبلغ تسویه را وارد کنید")
                            payment > old.amount -> toast("مبلغ تسویه نمی تواند بیشتر از بدهی باشد")
                            !validDate(d) -> toast("تاریخ را به شکل 1405/01/01 وارد کنید")
                            else -> { updateShopDebt(id, old.amount - payment, d, payment); toast("تسویه ثبت شد"); dialog.dismiss(); showShopDebts() }
                        }
                    }
                }
            }.show()
    }

    private fun saveShopDebt(existingId: Long?, name: String, newAmount: Long, date: String) {
        val arr = readArray("shop_debts")
        if (existingId == null) {
            val o = JSONObject().apply {
                put("id", System.currentTimeMillis()); put("name", name); put("amount", newAmount); put("date", date)
                put("payments", JSONArray()); put("history", JSONArray().apply { put(JSONObject().apply { put("amount", newAmount); put("date", date); put("type", "debt") }) })
            }
            arr.put(o)
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                if (o.optLong("id") == existingId) {
                    val oldAmount = o.optLong("amount")
                    val history = o.optJSONArray("history") ?: JSONArray()
                    val delta = newAmount - oldAmount
                    if (delta != 0L) history.put(JSONObject().apply { put("amount", delta); put("date", date); put("type", if (delta > 0) "debt" else "edit") })
                    o.put("name", name); o.put("amount", newAmount); o.put("date", date); o.put("history", history); arr.put(i, o); break
                }
            }
        }
        prefs.edit().putString("shop_debts", arr.toString()).apply()
        toast("ثبت شد")
    }

    private fun updateShopDebt(id: Long, newBalance: Long, date: String, payment: Long) {
        val arr = readArray("shop_debts")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("id") == id) {
                o.put("amount", newBalance); o.put("date", date)
                val payments = o.optJSONArray("payments") ?: JSONArray()
                payments.put(JSONObject().apply { put("amount", payment); put("date", date) })
                o.put("payments", payments)
                val history = o.optJSONArray("history") ?: JSONArray()
                history.put(JSONObject().apply { put("amount", -payment); put("date", date); put("type", "payment") })
                o.put("history", history); arr.put(i, o); break
            }
        }
        prefs.edit().putString("shop_debts", arr.toString()).apply()
    }

    // ---------- Entry dialogs ----------
    private fun companyAddDialog(category: String, existingId: Long? = null, defaultDate: String = companyDate) {
        val old = existingId?.let { findById("company", it) }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 18f; minHeight = 58; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: defaultDate); textSize = 18f; minHeight = 58 }
        val day = tv(displayDate(old?.date ?: defaultDate), 14f, blue, Gravity.RIGHT); date.addTextChangedListener(simpleDateWatcher(date, day))
        val box = vertical(amount, date, day)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val v = parseMoney(amount.text.toString()); val d = date.text.toString().trim()
                        if (v <= 0 || !validDate(d)) toast("مبلغ و تاریخ را درست وارد کنید")
                        else { saveEntry("company", Entry(existingId ?: System.currentTimeMillis(), category, v, d)); dialog.dismiss(); showCompany(d) }
                    }
                }
            }.show()
    }

    private fun expenseAddDialog(category: String, worker: Boolean = false, existingId: Long? = null, defaultDate: String = expenseDate) {
        val old = existingId?.let { findById("expense", it) }
        val name = EditText(this).apply { hint = "نام کارگر"; visibility = if (worker) View.VISIBLE else View.GONE; setText(old?.name ?: ""); textSize = 18f; minHeight = 58 }
        val amount = EditText(this).apply { hint = "مبلغ به ریال"; inputType = InputType.TYPE_CLASS_NUMBER; setText(if (old == null) "" else money(old.amount)); textSize = 18f; minHeight = 58; formatMoneyInput(this) }
        val date = EditText(this).apply { hint = "تاریخ شمسی"; setText(old?.date ?: defaultDate); textSize = 18f; minHeight = 58 }
        val day = tv(displayDate(old?.date ?: defaultDate), 14f, blue, Gravity.RIGHT); date.addTextChangedListener(simpleDateWatcher(date, day))
        val box = vertical(name, amount, date, day)
        AlertDialog.Builder(this).setTitle(if (old == null) "ثبت $category" else "ویرایش $category").setView(box)
            .setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val v = parseMoney(amount.text.toString()); val n = name.text.toString().trim(); val d = date.text.toString().trim()
                        if (v <= 0 || !validDate(d) || (worker && n.isEmpty())) toast("اطلاعات را کامل و درست وارد کنید")
                        else { saveEntry("expense", Entry(existingId ?: System.currentTimeMillis(), category, v, d, n)); dialog.dismiss(); showExpenses(d) }
                    }
                }
            }.show()
    }

    private fun addEntriesForCategory(parent: LinearLayout, store: String, category: String, date: String) {
        val arr = readArray(store); var count = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i); if (o.optString("category") != category || o.optString("date") != date) continue
            count++; val id = o.optLong("id"); val amount = o.optLong("amount"); val name = o.optString("name")
            val title = if (store == "expense" && category == "هزینه کارگر") "$name — ${money(amount)} ریال" else "${money(amount)} ریال"
            parent.addView(recordRow(title, o.optString("date"),
                { if (store == "company") companyAddDialog(category, id, date) else expenseAddDialog(category, category == "هزینه کارگر", id, date) },
                { deleteById(store, id); if (store == "company") showCompany(date) else showExpenses(date) }), lp(0, 6))
        }
        if (count == 0) parent.addView(empty("برای ${displayDate(date)} هنوز ثبت نشده است."), lp(0, 2))
    }

    private fun recordRow(title: String, date: String, edit: () -> Unit, delete: () -> Unit): LinearLayout {
        val row = cardBox()
        row.addView(tv(title, 17f, text, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, lp())
        row.addView(tv("تاریخ: ${displayDate(date)}", 13f, gray, Gravity.RIGHT), lp(0, 7))
        row.addView(actionButtonRow("ویرایش ثبت", edit), lp(0, 7))
        row.addView(actionButtonRow("حذف ثبت", delete), lp(0, 7))
        return row
    }

    // ---------- Reports ----------
    private fun showReports() { showReportsFor(Period(todayJalali(), todayJalali(), "امروز")) }

    private fun showReportsFor(initial: Period) {
        val r = baseRoot("گزارش گیری", true)
        r.addView(sectionTitle("گزارش جزئی یا تراز کلی"), lp())
        r.addView(infoBox("بازه گزارش", "${initial.title}: ${initial.from} تا ${initial.to}"), lp(0, 4))
        r.addView(actionRow("انتخاب بازه گزارش") { periodDialog { p -> showReportsFor(p) } }, lp(0, 7))
        val categories = linkedMapOf(
            "گزارش کلی / تراز" to "__ALL__", "مبلغ بارنامه" to "مبلغ بارنامه", "ضایعات" to "ضایعات", "کارتخوان" to "کارتخوان", "پول نقد" to "پول نقد", "تخفیفات" to "تخفیفات", "برگشتی" to "برگشتی",
            "بدهی و طلب روزانه مغازه دارها" to "__DEBT_MOVEMENT__", "مانده بدهی مغازه دارها" to "__DEBTS__", "گازوئیل" to "گازوئیل", "تعمیرات" to "تعمیرات", "خرید قطعات" to "خرید قطعات", "تعمیر یخچال" to "تعمیر یخچال", "هزینه کارگر" to "هزینه کارگر", "هزینه بیمه" to "هزینه بیمه", "تسویه های مغازه دارها" to "__PAYMENTS__"
        )
        categories.forEach { (label, key) -> r.addView(actionRow("گزارش $label") { showSingleReport(initial, label, key) }, lp(0, 5)) }
        r.addView(actionRow("بازگشت به صفحه اصلی") { showDashboard() }, lp(0, 18))
        setContentView(scroll(r))
    }

    private fun showSingleReport(period: Period, label: String, key: String) {
        val r = baseRoot(label, true); r.addView(infoBox("بازه گزارش", "${period.title}: ${period.from} تا ${period.to}"), lp(0, 5))
        when (key) {
            "__ALL__" -> r.addView(reportOverall(period), lp(0, 8))
            "__DEBTS__" -> r.addView(reportDebts(period), lp(0, 8))
            "__DEBT_MOVEMENT__" -> r.addView(reportDebtMovement(period), lp(0, 8))
            "__PAYMENTS__" -> r.addView(reportPayments(period), lp(0, 8))
            else -> {
                val store = if (listOf("گازوئیل", "تعمیرات", "خرید قطعات", "تعمیر یخچال", "هزینه کارگر", "هزینه بیمه").contains(key)) "expense" else "company"
                val arr = readArray(store); var total = 0L; var count = 0; val box = cardBox()
                for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("category") == key && inPeriod(o.optString("date"), period)) { val amount = o.optLong("amount"); total += amount; count++; val n = o.optString("name"); val title = if (key == "هزینه کارگر" && n.isNotBlank()) "$n — ${money(amount)} ریال" else "${money(amount)} ریال"; addSummaryLine(box, title, amount, text); box.addView(tv("${displayDate(o.optString("date"))}", 12f, gray, Gravity.RIGHT), lp(0, 1)) } }
                addSummaryLine(box, "تعداد ثبت ها", count.toLong(), blue); addSummaryLine(box, "جمع $label", total, green); if (count == 0) box.addView(empty("در این بازه ثبت نشده است."), lp(0, 5)); r.addView(box, lp(0, 8))
            }
        }
        r.addView(actionRow("تغییر بازه گزارش") { periodDialog { p -> showSingleReport(p, label, key) } }, lp(0, 8))
        r.addView(actionRow("بازگشت به گزارش ها") { showReportsFor(period) }, lp(0, 8)); setContentView(scroll(r))
    }

    private fun reportOverall(period: Period): LinearLayout {
        val b = cardBox()
        val barnameh = sumCategoryInPeriod("مبلغ بارنامه", period); val waste = sumCategoryInPeriod("ضایعات", period); val card = sumCategoryInPeriod("کارتخوان", period); val cash = sumCategoryInPeriod("پول نقد", period); val discount = sumCategoryInPeriod("تخفیفات", period); val returns = sumCategoryInPeriod("برگشتی", period)
        val addedDebt = shopDebtNewInPeriod(period); val collected = shopDebtCollectionsInPeriod(period); val adjustedBarnameh = barnameh + collected; val settlement = waste + card + cash + discount + returns + addedDebt; val diff = settlement - adjustedBarnameh
        val baseProfit = ((adjustedBarnameh - waste).coerceAtLeast(0L) * 57L) / 1000L; val expenses = expenseTotalInPeriod(period); val extra = diff.coerceAtLeast(0L); val companyDebt = (-diff).coerceAtLeast(0L); val net = baseProfit + extra - expenses
        addSummaryLine(b, "مبلغ بارنامه", barnameh, deepBlue); addSummaryLine(b, "ضایعات", waste, text); addSummaryLine(b, "کارتخوان", card, text); addSummaryLine(b, "پول نقد", cash, text); addSummaryLine(b, "بدهی جدید مغازه دارها", addedDebt, text); addSummaryLine(b, "وصولی طلب قبلی", collected, text); addSummaryLine(b, "تخفیفات", discount, text); addSummaryLine(b, "برگشتی", returns, text); addSummaryLine(b, "بارنامه مبنا", adjustedBarnameh, deepBlue); addSummaryLine(b, "جمع تسویه", settlement, blue)
        addSummaryLine(b, if (diff > 0) "سود اضافه حساب" else if (diff < 0) "بدهی باقی مانده" else "تسویه کامل", kotlin.math.abs(diff), if (diff < 0) red else green); addSummaryLine(b, "سود پایه ۵.۷٪", baseProfit, green); addSummaryLine(b, "هزینه ها", expenses, red); addSummaryLine(b, "سود خالص تقریبی", net, if (net >= 0) green else red); addSummaryLine(b, "کل مانده بدهی فعلی مغازه دارها", shopDebtTotal(), text)
        return b
    }

    private fun reportDebtMovement(period: Period): LinearLayout {
        val b = cardBox(); var newTotal = 0L; var collectedTotal = 0L; var days = 0
        var d = period.from
        while (d <= period.to) {
            val n = shopDebtNewOnDate(d); val c = shopDebtCollectionsOnDate(d)
            if (n != 0L || c != 0L) {
                if (n != 0L) { addSummaryLine(b, "${displayDate(d)} • بدهی جدید", n, red); newTotal += n }
                if (c != 0L) { addSummaryLine(b, "${displayDate(d)} • وصولی طلب قبلی", c, green); collectedTotal += c }
                days++
            }
            d = nextJalaliDate(d)
        }
        addSummaryLine(b, "جمع بدهی جدید در بازه", newTotal, red)
        addSummaryLine(b, "جمع وصولی طلب قبلی در بازه", collectedTotal, green)
        if (days == 0) b.addView(empty("در این بازه تغییری در بدهی ثبت نشده است."), lp(0, 5))
        return b
    }

    private fun reportDebts(period: Period): LinearLayout {
        val b = cardBox(); val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val amount = o.optLong("amount"); total += amount; addSummaryLine(b, o.optString("name"), amount, if (amount == 0L) green else red); b.addView(tv("مانده فعلی • آخرین ثبت: ${displayDate(o.optString("date"))}", 12f, gray, Gravity.RIGHT), lp(0, 1)) }
        addSummaryLine(b, "جمع مانده بدهی فعلی", total, blue); if (arr.length() == 0) b.addView(empty("هیچ بدهکاری ثبت نشده است."), lp(0, 5)); return b
    }

    private fun reportPayments(period: Period): LinearLayout {
        val b = cardBox(); val payments = reportPaymentsData(period); var total = 0L
        payments.forEach { p -> total += p.second; addSummaryLine(b, p.first, p.second, green); b.addView(tv("تاریخ: ${displayDate(p.third)}", 12f, gray, Gravity.RIGHT), lp(0, 1)) }
        addSummaryLine(b, "جمع تسویه های بازه", total, blue); if (payments.isEmpty()) b.addView(empty("در این بازه تسویه ای ثبت نشده است."), lp(0, 5)); return b
    }

    private fun reportPaymentsData(period: Period): List<Triple<String, Long, String>> {
        val out = mutableListOf<Triple<String, Long, String>>(); val arr = readArray("shop_debts")
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); val name = o.optString("name"); val p = o.optJSONArray("payments") ?: JSONArray(); for (j in 0 until p.length()) { val po = p.getJSONObject(j); val date = po.optString("date"); if (inPeriod(date, period)) out.add(Triple(name, po.optLong("amount"), date)) } }
        return out
    }

    private fun periodDialog(onDone: (Period) -> Unit) {
        AlertDialog.Builder(this).setTitle("انتخاب بازه گزارش").setItems(arrayOf("امروز", "این ماه", "بازه دلخواه")) { _, which ->
            when (which) { 0 -> onDone(Period(todayJalali(), todayJalali(), "امروز")); 1 -> { val t = todayJalali(); val month = t.substring(0, 7); onDone(Period("$month/01", "$month/31", "این ماه")) }; 2 -> customPeriodDialog(onDone) }
        }.show()
    }

    private fun customPeriodDialog(onDone: (Period) -> Unit) {
        val from = EditText(this).apply { hint = "از تاریخ 1405/01/01"; textSize = 18f; minHeight = 58 }
        val to = EditText(this).apply { hint = "تا تاریخ 1405/01/31"; textSize = 18f; minHeight = 58 }
        val box = vertical(from, to)
        AlertDialog.Builder(this).setTitle("بازه دلخواه شمسی").setView(box).setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog -> dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val f = from.text.toString().trim(); val t = to.text.toString().trim(); if (!validDate(f) || !validDate(t) || f > t) toast("تاریخ ها را به شکل 1405/01/01 درست وارد کنید") else { dialog.dismiss(); onDone(Period(f, t, "بازه دلخواه")) } } } }.show()
    }

    private fun dateDialog(current: String, onDone: (String) -> Unit) {
        val date = EditText(this).apply { hint = "1405/01/01"; setText(current); textSize = 18f; minHeight = 58 }
        val day = tv(displayDate(current), 14f, blue, Gravity.RIGHT); date.addTextChangedListener(simpleDateWatcher(date, day))
        val box = vertical(date, day)
        AlertDialog.Builder(this).setTitle("انتخاب تاریخ شمسی").setMessage("برای روزهای قبل هم می توانید تاریخ را وارد و ثبت کنید.").setView(box).setPositiveButton("ثبت", null).setNegativeButton("انصراف", null).create().also { dialog -> dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { val d = date.text.toString().trim(); if (!validDate(d)) toast("تاریخ را به شکل 1405/01/01 وارد کنید") else { dialog.dismiss(); onDone(d) } } } }.show()
    }

    private fun validDate(s: String): Boolean = Regex("\\d{4}/\\d{2}/\\d{2}").matches(s)
    private fun inPeriod(date: String, period: Period): Boolean = validDate(date) && date >= period.from && date <= period.to

    // ---------- Storage ----------
    private fun saveEntry(store: String, entry: Entry) {
        val arr = readArray(store); var replaced = false
        for (i in 0 until arr.length()) if (arr.getJSONObject(i).optLong("id") == entry.id) { arr.put(i, entry.toJson()); replaced = true; break }
        if (!replaced) arr.put(entry.toJson()); prefs.edit().putString(store, arr.toString()).apply(); toast("ثبت شد")
    }

    private fun Entry.toJson() = JSONObject().apply { put("id", id); put("category", category); put("amount", amount); put("date", date); put("name", name) }

    private fun findById(store: String, id: Long): Entry? { val arr = readArray(store); for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optLong("id") == id) return Entry(id, o.optString("category"), o.optLong("amount"), o.optString("date"), o.optString("name")) }; return null }

    private fun deleteById(store: String, id: Long) { val arr = readArray(store); val out = JSONArray(); for (i in 0 until arr.length()) if (arr.getJSONObject(i).optLong("id") != id) out.put(arr.getJSONObject(i)); prefs.edit().putString(store, out.toString()).apply() }
    private fun readArray(key: String) = JSONArray(prefs.getString(key, "[]") ?: "[]")

    private fun migrateDebtHistory() {
        val arr = readArray("shop_debts"); var changed = false
        for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (!o.has("history")) { o.put("history", JSONArray().apply { put(JSONObject().apply { put("amount", o.optLong("amount")); put("date", o.optString("date").ifBlank { todayJalali() }); put("type", "legacy") }) }); if (!o.has("payments")) o.put("payments", JSONArray()); arr.put(i, o); changed = true } }
        if (changed) prefs.edit().putString("shop_debts", arr.toString()).apply()
    }

    private fun shopDebtTotal(): Long { val arr = readArray("shop_debts"); var total = 0L; for (i in 0 until arr.length()) total += arr.getJSONObject(i).optLong("amount"); return total }

    private fun shopDebtNewOnDate(date: String): Long {
        val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) { val h = arr.getJSONObject(i).optJSONArray("history") ?: JSONArray(); for (j in 0 until h.length()) { val x = h.getJSONObject(j); val amount = x.optLong("amount"); if (x.optString("date") == date && amount > 0) total += amount } }
        return total
    }

    private fun shopDebtCollectionsOnDate(date: String): Long {
        val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) { val h = arr.getJSONObject(i).optJSONArray("history") ?: JSONArray(); for (j in 0 until h.length()) { val x = h.getJSONObject(j); val amount = x.optLong("amount"); if (x.optString("date") == date && amount < 0) total += -amount } }
        return total
    }

    private fun shopDebtNewInPeriod(period: Period): Long {
        val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) { val h = arr.getJSONObject(i).optJSONArray("history") ?: JSONArray(); for (j in 0 until h.length()) { val x = h.getJSONObject(j); val amount = x.optLong("amount"); if (inPeriod(x.optString("date"), period) && amount > 0) total += amount } }
        return total
    }

    private fun shopDebtCollectionsInPeriod(period: Period): Long {
        val arr = readArray("shop_debts"); var total = 0L
        for (i in 0 until arr.length()) { val h = arr.getJSONObject(i).optJSONArray("history") ?: JSONArray(); for (j in 0 until h.length()) { val x = h.getJSONObject(j); val amount = x.optLong("amount"); if (inPeriod(x.optString("date"), period) && amount < 0) total += -amount } }
        return total
    }

    private fun expenseTotalOnDate(date: String): Long { val arr = readArray("expense"); var total = 0L; for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("date") == date) total += o.optLong("amount") }; return total }
    private fun expenseTotalInPeriod(period: Period): Long { val arr = readArray("expense"); var total = 0L; for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (inPeriod(o.optString("date"), period)) total += o.optLong("amount") }; return total }
    private fun sumCategoryOnDate(category: String, date: String): Long { val arr = readArray("company"); var total = 0L; for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("category") == category && o.optString("date") == date) total += o.optLong("amount") }; return total }
    private fun sumCategoryInPeriod(category: String, period: Period): Long { val arr = readArray("company"); var total = 0L; for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); if (o.optString("category") == category && inPeriod(o.optString("date"), period)) total += o.optLong("amount") }; return total }

    // ---------- UI ----------
    private fun baseRoot(title: String, backVisible: Boolean): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); layoutDirection = View.LAYOUT_DIRECTION_RTL }
        val bar = LinearLayout(this).apply { setBackgroundColor(deepBlue); gravity = Gravity.CENTER_VERTICAL; setPadding(8, 8, 8, 8); layoutDirection = View.LAYOUT_DIRECTION_RTL; minimumHeight = 78 }
        if (backVisible) bar.addView(TextView(this).apply { text = "‹"; textSize = 40f; setTextColor(white); gravity = Gravity.CENTER; setOnClickListener { showDashboard() } }, LinearLayout.LayoutParams(58, 72))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        col.addView(tv(title, 23f, white, Gravity.CENTER).apply { typeface = Typeface.DEFAULT_BOLD }, lp())
        if (title != "حسابیار لبنیات") col.addView(tv("ثبت و کنترل حساب روزانه", 12f, Color.rgb(220, 228, 247), Gravity.CENTER), lp(0, 3))
        bar.addView(col, LinearLayout.LayoutParams(0, 72, 1f)); r.addView(bar, lp()); return r
    }

    private fun actionRow(title: String, click: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(18, 8, 18, 8); minimumHeight = 74; background = rounded(white, 18, Color.rgb(211, 220, 237)); setOnClickListener { click() }; layoutDirection = View.LAYOUT_DIRECTION_RTL; elevation = 1f }
        row.addView(tv(title, 18f, text, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0, 64, 1f))
        row.addView(tv("＋", 30f, blue, Gravity.CENTER), LinearLayout.LayoutParams(58, 62)); return row
    }

    private fun actionButtonRow(title: String, click: () -> Unit): Button {
        return Button(this).apply { text = title; textSize = 15f; minHeight = 54; isAllCaps = false; setTextColor(deepBlue); setOnClickListener { click() }; background = rounded(Color.rgb(235, 241, 253), 15, Color.rgb(188, 201, 229)); setPadding(10, 0, 10, 0) }
    }

    private fun infoBox(title: String, value: String): LinearLayout { val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 16, 20, 16); background = rounded(Color.rgb(233, 239, 251), 19, Color.rgb(184, 198, 227)); minimumHeight = 90 }; b.addView(tv(title, 15f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD }, lp()); b.addView(tv(value, 19f, blue, Gravity.RIGHT), lp(0, 7)); return b }
    private fun cardBox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 18); background = rounded(white, 20, Color.rgb(211, 220, 237)); elevation = 2f }
    private fun sectionTitle(s: String) = tv(s, 20f, deepBlue, Gravity.RIGHT).apply { typeface = Typeface.DEFAULT_BOLD; setPadding(6, 16, 6, 10); minimumHeight = 54 }
    private fun empty(s: String) = tv(s, 14f, gray, Gravity.RIGHT).apply { setPadding(10, 7, 10, 7); minimumHeight = 42 }
    private fun tv(s: String, size: Float, color: Int, gravity: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); this.gravity = gravity; layoutDirection = View.LAYOUT_DIRECTION_RTL; includeFontPadding = true; setLineSpacing(4f, 1.08f) }
    private fun vertical(vararg views: View) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 4); views.forEach { addView(it, lp(0, 12)) } }
    private fun scroll(content: LinearLayout) = ScrollView(this).apply { setBackgroundColor(bg); isFillViewport = true; addView(content) }
    private fun lp(w: Int = -1, margin: Int = 0) = LinearLayout.LayoutParams(if (w == 0) -1 else w, LinearLayout.LayoutParams.WRAP_CONTENT).apply { if (margin > 0) setMargins(0, margin, 0, margin) }
    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); stroke?.let { setStroke(2, it) } }
    private fun addSummaryLine(parent: LinearLayout, label: String, amount: Long, color: Int) { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 7, 0, 7); layoutDirection = View.LAYOUT_DIRECTION_RTL; minimumHeight = 46 }; row.addView(tv(label, 15.5f, gray, Gravity.RIGHT), LinearLayout.LayoutParams(0, -2, 1f)); row.addView(tv("${money(amount)} ریال", 16f, color, Gravity.LEFT), LinearLayout.LayoutParams(-2, -2)); parent.addView(row) }

    private fun formatMoneyInput(edit: EditText) { edit.addTextChangedListener(object : TextWatcher { private var busy = false; override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} override fun afterTextChanged(e: Editable?) { if (busy) return; val raw = e?.toString()?.replace(",", "")?.replace("٬", "")?.trim() ?: return; if (raw.isEmpty()) return; val n = raw.toLongOrNull() ?: return; val f = money(n); if (f != e.toString()) { busy = true; edit.setText(f); edit.setSelection(f.length); busy = false } } }) }
    private fun parseMoney(s: String): Long = s.replace(",", "").replace("٬", "").replace("ریال", "").trim().toLongOrNull() ?: 0L
    private fun money(v: Long): String = DecimalFormat("#,###", DecimalFormatSymbols(Locale.US)).format(v)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun simpleDateWatcher(edit: EditText, day: TextView) = object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {} override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {} override fun afterTextChanged(s: Editable?) { val d = s?.toString()?.trim() ?: ""; day.text = if (validDate(d)) displayDate(d) else "روز هفته پس از ورود تاریخ نمایش داده می شود" } }

    // ---------- Jalali dates ----------
    private fun todayJalali(): String { val c = Calendar.getInstance(); val j = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)); return "%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2]) }

    private fun displayDate(jalali: String): String {
        if (!validDate(jalali)) return jalali
        val p = jalali.split("/"); val g = jalaliToGregorian(p[0].toInt(), p[1].toInt(), p[2].toInt()); val c = Calendar.getInstance(); c.set(g[0], g[1] - 1, g[2]); val names = arrayOf("یکشنبه", "دوشنبه", "سه شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"); return "${names[c.get(Calendar.DAY_OF_WEEK) - 1]} ${p[0]}/${p[1]}/${p[2]}"
    }

    private fun nextJalaliDate(date: String): String { if (!validDate(date)) return date; val p = date.split("/"); val g = jalaliToGregorian(p[0].toInt(), p[1].toInt(), p[2].toInt()); val c = Calendar.getInstance(); c.set(g[0], g[1] - 1, g[2]); c.add(Calendar.DAY_OF_MONTH, 1); val j = gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)); return "%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2]) }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray { val gdm = intArrayOf(0,31,28,31,30,31,30,31,31,30,31,30,31); val jdm = intArrayOf(0,31,31,31,31,31,31,30,30,30,30,30,29); var y = gy - 1600; val m = gm - 1; val d = gd - 1; var days = 365*y + (y+3)/4 - (y+99)/100 + (y+399)/400; for (i in 0 until m) days += gdm[i+1]; if (m > 1 && ((gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0)) days++; days += d; var jdays = days - 79; var jy = 979 + 33*(jdays/12053); jdays %= 12053; var jyy = jy + 4*(jdays/1461); jdays %= 1461; if (jdays >= 366) { jyy += (jdays-1)/365; jdays = (jdays-1)%365 }; var jm = 0; while (jm < 11 && jdays >= jdm[jm+1]) { jdays -= jdm[jm+1]; jm++ }; return intArrayOf(jyy, jm+1, jdays+1) }

    private fun jalaliToGregorian(jy0: Int, jm0: Int, jd0: Int): IntArray { var jy = jy0 - 979; var jm = jm0 - 1; val jd = jd0 - 1; var days = 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4; for (i in 0 until jm) days += if (i < 6) 31 else 30; days += jd; var gdays = days + 79; var gy = 1600 + 400 * (gdays / 146097); gdays %= 146097; var leap = true; if (gdays >= 36525) { gdays--; gy += 100 * (gdays / 36524); gdays %= 36524; if (gdays >= 365) gdays++ else leap = false }; gy += 4 * (gdays / 1461); gdays %= 1461; if (gdays >= 366) { leap = false; gdays--; gy += gdays / 365; gdays %= 365 }; val gdm = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31); var gm = 0; while (gm < 12 && gdays >= gdm[gm]) { gdays -= gdm[gm]; gm++ }; return intArrayOf(gy, gm + 1, gdays + 1) }
}
