package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.TransactionDraft
import com.sadique.dailyledger.data.ExpandedItemCategories
import com.sadique.dailyledger.data.ItemCategories
import java.util.Locale

/**
 * Canonical AI category rules for Daily Ledger.
 *
 * AI-created entries should use the app's real categories instead of inventing
 * labels such as "Other groceries" or "Other kameti". "Other" is deliberately
 * the last fallback after trying the known category vocabulary.
 */
object CategoryRules {
    private data class Rule(val category: String, val aliases: List<String>)

    private val dedicatedLedgerAliases = listOf(
        "loan", "udhar", "qarz", "قرض",
        "kameti", "committee", "کمیٹی",
        "saving", "savings", "bachat", "بچت",
    )

    // Order matters: specific categories are checked before broader ones.
    private val expenseRules = ItemCategories.entries.map { Rule(it.label, it.aliases) } + listOf(
        Rule("School transport", listOf("school van", "school bus", "school transport", "van")),
        Rule("Vehicle insurance", listOf("car insurance", "vehicle insurance", "bike insurance")),
        Rule("Health insurance", listOf("health insurance", "medical insurance")),
        Rule("Religious travel", listOf("umrah", "hajj")),
        Rule("Sadqa & donations", listOf("sadqa", "sadaqah", "donation", "charity", "khairat", "fitrana", "fitra")),
        Rule("Weddings & events", listOf("shaadi", "shadi", "wedding", "walima", "event")),
        Rule("Eid & festivals", listOf("eid", "festival", "qurbani", "bakra eid")),
        Rule("Zakat", listOf("zakat", "زکوٰۃ", "زکات")),
        Rule("Spouse personal expenses", listOf("wife expenses", "wife personal", "wife kharcha", "biwi ka kharcha", "begum personal", "begum kharcha", "zauja expense")),
        Rule("Wife's expenses", listOf("wife expenses", "wife kharcha", "biwi ka kharcha", "begum ka kharcha")),
        Rule("Father medical", listOf("father medical", "father medical expenses", "father medicine", "father doctor", "abbu ki dawa", "walid ki dawa")),
        Rule("Mother medical", listOf("mother medical", "mother medical expenses", "mother medicine", "mother doctor", "ammi ki dawa", "walida ki dawa")),
        Rule("Wife medical", listOf("wife medical", "wife medical expenses", "wife medicine", "wife doctor", "biwi ki dawa", "begum ki dawa")),
        Rule("Baby medical", listOf("baby medical", "baby medical expenses", "baby medicine", "baby doctor", "baby ki dawa")),
        Rule("Son medical", listOf("son medical", "son medical expenses", "son medicine", "son doctor", "beta ki dawa", "bete ki dawa")),
        Rule("Daughter medical", listOf("daughter medical", "daughter medical expenses", "daughter medicine", "daughter doctor", "beti ki dawa")),
        Rule("Son fees", listOf("son fee", "son fees", "beta fee", "betay ki fee", "bete ki fee")),
        Rule("Daughter fees", listOf("daughter fee", "daughter fees", "beti fee", "beti ki fee", "betiyon ki fee")),
        Rule("Daughter travel", listOf("daughter travel", "daughter trip", "beti ka safar", "beti ka trip")),
        Rule("Household cash", listOf("daily household cash", "household cash", "ghar ka daily kharcha", "ghar ka kharcha", "ghar chalane ke paise")),
        Rule("Daily household cash", listOf("ghar ka daily kharcha", "ghar chalane ke roz ke paise")),
        Rule("Children allowance", listOf("children allowance", "kids allowance", "bachon ka pocket money")),
        Rule("Personal allowance", listOf("personal allowance", "my pocket money", "mera jaib kharch")),
        Rule("Children activities", listOf("children activities", "kids activity", "school trip", "school picnic")),
        Rule("School meals", listOf("school lunch", "school meal", "school canteen", "lunch money")),
        Rule("Children healthcare", listOf("child doctor", "children doctor", "kids medicine", "bachon ki dawa")),
        Rule("Elder care", listOf("elder care", "parents medicine", "parents care", "buzurg")),
        Rule("Children fees", listOf("children fee", "children fees", "kids fee", "bachon ki fees", "bache ki fees")),

        Rule("Groceries", listOf("rashan", "ration", "grocery", "groceries", "راشن")),
        Rule("Flour", listOf("aata", "atta", "flour", "آٹا")),
        Rule("Rice", listOf("chawal", "rice", "چاول")),
        Rule("Lentils", listOf("daal", "dal", "lentil", "lentils", "دال")),
        Rule("Cooking oil", listOf("cooking oil", "ghee", "tel", "oil", "گھی")),
        Rule("Garam masala", listOf("garam masala")),
        Rule("Ground coriander", listOf("sukha dhaniya", "dhania powder", "ground coriander", "coriander powder")),
        Rule("Turmeric", listOf("haldi", "turmeric")),
        Rule("Chilli powder", listOf("lal mirch", "laal mirch", "lal march", "laal march", "surkh mirch", "surkh march", "red chilli", "chilli powder", "chili powder")),
        Rule("Cumin & seeds", listOf("zeera", "cumin", "ajwain", "kalonji", "rai")),
        Rule("Salt", listOf("namak", "salt")),
        Rule("Condiments & sauces", listOf("ketchup", "soy sauce", "mayonnaise", "vinegar", "achar", "chatni", "chutney")),
        Rule("Spices", listOf("masala", "spice", "spices", "مصالحہ")),
        Rule("Sugar & tea", listOf("cheeni", "sugar", "patti", "tea leaves", "چینی")),
        Rule("Milk", listOf("doodh", "dodh", "milk", "دودھ")),
        Rule("Vegetables", listOf("sabzi", "sabziyan", "vegetable", "vegetables", "سبزی")),
        Rule("Fruit", listOf("phal", "fruit", "fruits", "پھل")),
        Rule("Meat & chicken", listOf("gosht", "meat", "chicken", "murghi", "murgi", "گوشت", "مرغی")),
        Rule("Fish", listOf("machli", "fish", "مچھلی")),
        Rule("Eggs & bread", listOf("anday", "anda", "eggs", "egg", "bread", "roti", "انڈے")),
        Rule("Bakery", listOf("bakery", "biscuit", "biscuits", "cake")),
        Rule("Dairy & breakfast", listOf("dahi", "yogurt", "yoghurt", "butter", "makhan", "cheese", "paneer", "jam", "cream")),
        Rule("Beverages", listOf("juice", "cold drink", "soft drink", "drink", "sharbat", "syrup", "pepsi", "coca cola", "coke", "sprite", "dew")),

        Rule("Takeaway", listOf("takeaway", "delivery", "foodpanda", "food panda")),
        Rule("Tea & coffee", listOf("chai", "coffee", "cafe", "چائے")),
        Rule("Snacks", listOf("snack", "snacks", "papar", "chips", "icecream", "ice cream", "samosa", "samosay", "gachak")),
        Rule("Restaurant", listOf("restaurant", "dinner", "lunch", "biryani", "burger", "pizza", "khana", "nashta", "breakfast", "کھانا")),

        Rule("Rent", listOf("house rent", "shop rent", "dukan rent", "makan kiraya", "kiraya makan", "makan ka kiraya", "rent")),
        Rule("Repairs", listOf("repair", "repairs", "plumber", "electrician", "mistri", "carpenter", "barhai", "water motor")),
        Rule("Furniture", listOf("furniture", "sofa", "bed", "table", "chair")),
        Rule("Appliances", listOf("appliance", "appliances", "fridge", "washing machine", "ac", "air conditioner", "stove", "choola", "chulha", "ups")),
        Rule("Phone accessories", listOf("phone accessories", "mobile cover", "phone cover", "charger", "earphones", "data cable", "handsfree", "headphones", "earbuds", "airbuds", "power bank")),
        Rule("Appliance maintenance", listOf("appliance maintenance", "fridge repair", "washing machine repair", "ac repair")),
        Rule("Home maintenance", listOf("home maintenance", "house maintenance", "ghar ki marammat", "painter", "paint")),
        Rule("Kitchen utensils", listOf("kitchen utensils", "cooking utensils", "bartan khareeday")),
        Rule("Bedding & linens", listOf("bedding", "bedsheet", "bed sheet", "blanket", "pillow")),
        Rule("Home security", listOf("home security", "security camera", "alarm", "door lock", "lock")),
        Rule("House cleaning help", listOf("house cleaning", "cleaning help", "safai wali", "sweeper", "jamadar")),
        Rule("Home insurance", listOf("home insurance", "house insurance", "makan insurance")),
        Rule("Building materials", listOf("building materials", "construction material", "cement", "bricks", "eent", "sand", "bajri")),
        Rule("Garden & outdoor", listOf("garden", "gardening", "lawn", "plants", "pauday")),
        Rule("Pest control", listOf("pest control", "termite spray", "fumigation", "deemak spray")),
        Rule("Solar maintenance", listOf("solar maintenance", "solar panel repair", "solar service", "solar panel", "solar")),
        Rule("Generator maintenance", listOf("generator maintenance", "generator repair", "generator service")),
        Rule("Laundry detergent", listOf("surf", "washing powder", "detergent powder", "laundry detergent", "washing liquid")),
        Rule("Cleaning supplies", listOf("safai", "cleaning", "detergent", "surf", "washing powder", "bleach", "phenyl", "floor cleaner", "harpic", "toilet cleaner")),
        Rule("Dishwashing", listOf("dishwash", "dish wash", "dishwashing", "vim", "vim bar", "bartan soap", "bartan sabun", "bartan liquid")),
        Rule("Household essentials", listOf("tissue", "tissues", "toilet roll", "kitchen roll", "foil", "aluminium foil", "garbage bag", "trash bag", "match box", "machis", "mosquito coil", "candle", "mombatti", "lalten", "lantern", "torch", "agarbatti")),
        Rule("Domestic help", listOf("maid", "helper", "house help", "nokrani")),
        Rule("Home supplies", listOf("bartan", "kitchen", "kitchen item", "kitchen items", "home supplies")),

        Rule("Electricity", listOf("bijli", "electricity", "electric bill", "electricity bill", "wapda", "بجلی")),
        Rule("Gas", listOf("gas bill", "sui gas", "sui", "gas", "سوئی گیس")),
        Rule("Water", listOf("water bill", "pani bill", "water", "pani", "پانی")),
        Rule("Drinking water", listOf("drinking water", "mineral water", "bottled water")),
        Rule("Water delivery", listOf("water delivery", "water tanker", "pani tanker")),
        Rule("Home internet", listOf("home internet", "internet bill", "wifi bill", "broadband bill", "ptcl")),
        Rule("Cooking gas cylinder", listOf("gas cylinder", "lpg cylinder", "cooking gas cylinder", "cylinder refill")),
        Rule("Internet", listOf("internet", "wifi", "wi-fi", "broadband")),
        Rule("Mobile package", listOf("mobile package", "mobile", "easyload", "easy load", "mobile load", "load", "package", "balance", "jazz", "telenor", "ufone", "zong", "sim", "new sim")),
        Rule("TV & cable", listOf("cable", "tv bill", "cable bill", "tv")),
        Rule("Data packages", listOf("data package", "internet package", "mobile data", "weekly package", "monthly package")),

        Rule("Fuel", listOf("petrol", "diesel", "fuel", "cng", "پٹرول")),
        Rule("Generator fuel", listOf("generator petrol", "generator diesel", "petrol for generator", "diesel for generator", "generator fuel")),
        Rule("Public transport", listOf("bus", "train", "metro", "public transport", "kiraya")),
        Rule("Taxi & rides", listOf("taxi", "rickshaw", "rikshaw", "careem", "indrive", "in drive", "uber", "fare")),
        Rule("Parking & tolls", listOf("parking", "toll", "tolls", "motorway toll")),
        Rule("Vehicle registration", listOf("vehicle registration", "car registration", "bike registration", "token tax")),
        Rule("Car wash", listOf("car wash", "bike wash", "vehicle wash")),
        Rule("Vehicle inspection", listOf("vehicle inspection", "car inspection", "fitness certificate")),
        Rule("Vehicle service", listOf("car service", "bike service", "vehicle service", "service", "mechanic", "oil change")),
        Rule("Vehicle parts", listOf("tyre", "tire", "parts", "vehicle parts", "car parts", "bike parts")),
        Rule("Work commute", listOf("work commute", "office commute", "office ka kiraya")),

        Rule("Medicines", listOf("dawa", "dawai", "medicine", "medicines", "pharmacy", "دوائی", "دوا")),
        Rule("Doctor", listOf("doctor", "clinic", "checkup", "check up")),
        Rule("Lab tests", listOf("lab", "test", "lab test", "lab tests", "blood test", "scan", "xray", "x-ray")),
        Rule("Hospital", listOf("hospital", "operation", "admission")),
        Rule("Dental", listOf("dentist", "dental", "tooth", "teeth")),
        Rule("Glasses", listOf("glasses", "chashma", "spectacles", "چشمہ")),
        Rule("Eyecare", listOf("eye care", "eyecare", "eye drops", "contact lenses")),
        Rule("Medical equipment", listOf("medical equipment", "wheelchair", "blood pressure machine", "thermometer")),

        Rule("School fees", listOf("school fee", "school fees", "monthly fee", "school ki fee", "bachon ki fee", "bache ki fee", "fees")),
        Rule("Admission & exam fees", listOf("admission fee", "admission fees", "exam fee", "exam fees", "paper fee", "registration fee", "annual charges", "school charges")),
        Rule("College & university", listOf("college", "university", "uni fee", "semester fee", "semester fees")),
        Rule("Tuition", listOf("tuition", "tutor", "academy", "academy fee", "tuition fee")),
        Rule("Books", listOf("books", "book", "kitab", "kitabein", "textbook", "textbooks", "course book", "کتاب")),
        Rule("Stationery", listOf("stationery", "stationary", "pen", "pencil", "copy", "copies", "notebook", "register", "eraser", "rubber", "sharpener", "marker", "highlighter", "scale", "ruler", "geometry", "geometry box", "file folder", "photocopy", "photostat")),
        Rule("School uniform", listOf("school uniform", "uniform", "school dress", "school shoes", "school belt", "school tie", "school socks")),
        Rule("School projects & supplies", listOf("school project", "project material", "chart", "chart paper", "colors", "colour", "crayons", "glue", "school bag", "school bottle", "lunch box", "school supplies")),
        Rule("Courses", listOf("course", "courses", "training")),
        Rule("Exam preparation", listOf("exam preparation", "test preparation", "entry test", "competitive exam")),

        Rule("Diapers & baby care", listOf("diaper", "diapers", "baby", "baby care", "pampers", "wipes", "baby wipes")),
        Rule("Baby food & formula", listOf("formula milk", "baby formula", "baby food", "cerelac", "lactogen", "nan milk", "baby cereal")),
        Rule("Toys", listOf("toy", "toys", "khilona", "khilonay")),
        Rule("Pocket money", listOf("pocket", "pocket money", "jaib kharch")),
        Rule("Family support", listOf("family", "family support", "parents", "walidain", "ghar walon ko")),
        Rule("Childcare", listOf("childcare", "daycare", "day care")),

        Rule("Clothing", listOf("kapray", "kapre", "clothes", "clothing", "suit", "dress", "garments", "darzi", "tailor", "silai", "stitching", "کپڑے")),
        Rule("Shoes", listOf("shoes", "shoe", "jootay", "jootey", "جوتے")),
        Rule("Haircut", listOf("haircut", "hair cut", "barber", "salon cut", "naai", "hajjam", "salon")),
        Rule("Cosmetics", listOf("cosmetic", "cosmetics", "makeup", "make up", "lipstick", "foundation", "nail polish")),
        Rule("Skincare", listOf("skincare", "skin care", "face wash", "moisturizer", "sunblock", "sunscreen")),
        Rule("Soap & body care", listOf("soap", "sabun", "saban", "saboon", "body wash", "shower gel", "body lotion", "lotion")),
        Rule("Soap & shampoo", listOf("soap and shampoo", "soap shampoo", "sabun shampoo")),
        Rule("Shampoo & hair care", listOf("shampoo", "conditioner", "hair oil", "hair serum", "hair cream", "shampoo sachet")),
        Rule("Oral care", listOf("toothpaste", "tooth paste", "toothbrush", "tooth brush", "manjan", "mouthwash", "mouth wash")),
        Rule("Sanitary & hygiene", listOf("sanitary pad", "sanitary pads", "pads", "napkin", "sanitary napkin", "tampon", "personal hygiene", "hand wash", "handwash", "sanitizer")),
        Rule("Toiletries", listOf("toiletries", "personal care item", "personal care items")),
        Rule("Laundry", listOf("laundry", "dhobi", "dry clean", "dryclean", "press", "istri", "ironing")),
        Rule("Electronics", listOf("electronics", "electronic item", "television", "led tv", "speaker")),
        Rule("Computer & accessories", listOf("computer", "laptop", "keyboard", "mouse", "computer accessories")),
        Rule("Device repair", listOf("device repair", "mobile repair", "phone repair", "laptop repair")),

        Rule("Subscriptions", listOf("subscription", "subscriptions", "netflix", "spotify", "youtube premium")),
        Rule("Cinema & outings", listOf("cinema", "movie", "outing", "picnic")),
        Rule("Games & hobbies", listOf("game", "games", "gaming", "hobby", "hobbies")),
        Rule("Gym & sports", listOf("gym", "sport", "sports", "fitness")),
        Rule("Travel & holidays", listOf("holiday", "holidays", "tour", "trip", "travel")),
        Rule("Hotel stay", listOf("hotel", "hotel stay", "lodging", "room booking")),

        Rule("Gifts", listOf("gift", "gifts", "tohfa", "tohfe", "phool", "flowers", "flower", "تحفہ")),
        Rule("Pet food", listOf("pet food", "cat food", "dog food")),
        Rule("Veterinary", listOf("vet", "veterinary", "pet doctor")),
        Rule("Pet supplies", listOf("pet supplies", "litter", "pet accessories")),

        Rule("Business expenses", listOf("business", "shop", "business expense", "business expenses", "shop expense", "dukan kharcha")),
        Rule("Work meals", listOf("work meal", "office lunch", "office meal")),
        Rule("Delivery charges", listOf("delivery charge", "delivery fee", "courier fee")),
        Rule("Office supplies", listOf("office", "office supplies", "office expense", "printer ink")),
        Rule("Bank fees", listOf("bank", "charges", "bank fee", "bank fees", "bank charges", "atm fee", "atm")),
        Rule("Taxes & documents", listOf("taxes", "passport", "document fee", "documents")),
        Rule("Property tax", listOf("property tax", "house tax", "makan tax")),
        Rule("Legal & professional fees", listOf("legal fee", "lawyer fee", "consultant fee", "professional fee")),
        Rule("Mosque & madrasa", listOf("mosque", "masjid", "madrasa", "madarsa", "mosque donation", "masjid donation", "madrasa donation", "مسجد", "مدرسہ")),
        Rule("Community contributions", listOf("community contribution", "community fund", "neighbourhood fund", "chanda")),
        Rule("Postage", listOf("courier", "postage", "parcel", "tcs")),
        Rule("Emergency", listOf("emergency", "urgent expense")),
    )

    private val incomeRules = listOf(
        Rule("Salary", listOf("salary", "tankhwa", "tankhwah", "tankha", "تنخواہ")),
        Rule("Bonus", listOf("bonus")),
        Rule("Freelance income", listOf("freelance", "freelancing", "client payment")),
        Rule("Rental income", listOf("rental income", "rent received", "kiraya mila", "kiraya received")),
        Rule("Business income", listOf("business income", "profit", "munafa", "sale income")),
        Rule("Gift received", listOf("gift received", "tohfa mila", "gift mila")),
        Rule("Refund", listOf("refund", "cashback", "cash back")),
        Rule("Pension", listOf("pension", "retirement income")),
        Rule("Overtime", listOf("overtime", "extra shift")),
        Rule("Commission income", listOf("commission income", "commission")),
        Rule("Agriculture income", listOf("agriculture income", "crop sale", "fasal ki income")),
        Rule("Sale of belongings", listOf("sold old phone", "sold furniture", "sale of belongings", "old item sale")),
        Rule("Side hustle income", listOf("side hustle", "side income", "part time income")),
        Rule("Insurance claim", listOf("insurance claim", "claim received")),
    )

    val expenseCategories: List<String> = (expenseRules.map { it.category } + "Other").distinct()
    val incomeCategories: List<String> = (incomeRules.map { it.category } + "Other income").distinct()
    val allCategories: List<String> = (expenseCategories + incomeCategories).distinct()

    fun isDedicatedLedgerText(text: String): Boolean {
        val normalized = normalizeText(text)
        return dedicatedLedgerAliases.any { containsAlias(normalized, it) }
    }

    fun infer(type: String, text: String): String {
        val normalized = normalizeText(text)
        val rules = if (type.equals("INCOME", ignoreCase = true)) incomeRules else expenseRules
        val pairs = rules.flatMap { rule -> (rule.aliases + rule.category).map { alias -> rule.category to normalizeText(alias) } }
        // Exact multi-word phrase match first — existing behaviour, unchanged.
        val exactMulti = pairs.filter { (_, alias) -> alias.contains(" ") && containsAlias(normalized, alias) }
            .maxByOrNull { (_, alias) -> alias.length }?.first
        if (exactMulti != null) return exactMulti
        // Token-set tier: every word of a multi-word alias present in the input in
        // any order — e.g. "salan chicken" finds "chicken salan", "patti chai"
        // finds "chai patti", "gas sui" finds "sui gas", "mirch laal" finds
        // "laal mirch". Longest alias wins, as above. This runs before the
        // single-word exact tier so a short single word (e.g. "patti" in
        // "Sugar & tea") cannot beat the full phrase the user meant.
        val inputWords = normalized.split(" ").filter { it.isNotEmpty() }.toSet()
        val tokenSet = pairs.filter { (_, alias) ->
            val aliasWords = alias.split(" ").filter { it.isNotEmpty() }
            aliasWords.size > 1 && aliasWords.all { it in inputWords }
        }.maxByOrNull { (_, alias) -> alias.length }?.first
        if (tokenSet != null) return tokenSet
        // Exact single-word match — existing behaviour, unchanged.
        val exact = pairs.filter { (_, alias) -> containsAlias(normalized, alias) }
            .maxByOrNull { (_, alias) -> alias.length }?.first
        if (exact != null) return exact
        // Fallback: a single word that starts with a single-word alias (or vice versa),
        // so near spellings still match — e.g. "macaronii" finds "macaroni", "parathay"
        // finds "paratha". Only words of 4+ letters count, so short words ("tea",
        // "oil", "gas", "dal") can never fuzzy-match something unrelated.
        val words = normalized.split(" ").filter { it.length >= 4 }
        val fuzzy = pairs.filter { (_, alias) ->
            val aliasWords = alias.split(" ").filter { it.length >= 4 }
            aliasWords.size == 1 && words.any { word -> word.startsWith(aliasWords[0]) || aliasWords[0].startsWith(word) }
        }.maxByOrNull { (_, alias) -> alias.length }?.first
        return fuzzy ?: if (type.equals("INCOME", ignoreCase = true)) "Other income" else "Other"
    }

    fun normalize(type: String, proposed: String, sourceText: String, learnedLabels: Set<String> = emptySet()): String {
        val isIncome = type.equals("INCOME", ignoreCase = true)
        val canonical = if (isIncome) incomeCategories else expenseCategories
        val proposedClean = proposed.trim()

        // A learned item label is a real category now: keep it verbatim
        // instead of letting inference collapse it back to Other.
        if (learnedLabels.any { it.equals(proposedClean, ignoreCase = true) }) {
            return learnedLabels.first { it.equals(proposedClean, ignoreCase = true) }
        }

        // Prefer the concrete item the user named over a broad model-made bucket.
        val concrete = infer(type, sourceText)
        val broadBuckets = setOf("other", "furniture", "home supplies", "dry fruits", "toiletries", "oral care",
            "soap & body care", "shampoo & hair care", "kitchen utensils", "appliances", "electronics", "stationery",
            "fruit", "vegetables", "condiments & sauces", "snacks", "cake & sweets", "juice & shakes")
        if (!isIncome && proposedClean.lowercase(Locale.ROOT) in broadBuckets &&
            ExpandedItemCategories.entries.any { it.label == concrete }) return concrete

        // Specific familiar food terms take precedence over a model's broad bucket.
        val specificFood = setOf("Salan", "Packaged milk", "Chocolate", "Toffee & candy", "Papar", "Biscuits", "Chips & nimko", "Chana breakfast", "Paratha", "Halwa puri")
        val inferredFood = infer(type, sourceText)
        if (!isIncome && inferredFood in specificFood && normalizeText(proposedClean) in setOf("other", "milk", "doodh", "snacks", "food", "food dining", "food & dining", "restaurant", "groceries", "grocery")) return inferredFood
        // Fix legacy/model-made labels first.
        val legacy = when (normalizeText(proposedClean)) {
            "other groceries", "other grocery", "grocery", "groceries", "rashan", "ration" -> "Groceries"
            "medical", "medicine" -> "Medicines"
            "bills", "electric bill", "electricity bill" -> "Electricity"
            "mobile", "mobile load" -> "Mobile package"
            "food", "food dining", "food & dining" -> "Restaurant"
            "shopping" -> infer("EXPENSE", sourceText).takeIf { it != "Other" } ?: "Clothing"
            "transport" -> infer("EXPENSE", sourceText).takeIf { it in setOf("Fuel", "Public transport", "Taxi & rides", "Parking & tolls", "Vehicle service", "Vehicle parts", "Vehicle insurance") } ?: "Public transport"
            "other income", "income" -> "Other income"
            else -> null
        }
        if (legacy != null && legacy in canonical) return legacy

        canonical.firstOrNull { it.equals(proposedClean, ignoreCase = true) }?.let { matched ->
            // Even if a model proposed Other, try the actual text one more time first.
            if (matched == "Other" || matched == "Other income") {
                val inferred = infer(type, sourceText)
                if (inferred != matched) return inferred
            }
            return matched
        }

        val inferred = infer(type, "$sourceText $proposedClean")
        if (inferred in canonical) return inferred
        return if (isIncome) "Other income" else "Other"
    }

    fun normalizeDraft(draft: TransactionDraft, learnedLabels: Set<String> = emptySet()): TransactionDraft =
        draft.copy(category = normalize(draft.type, draft.category, draft.note, learnedLabels)).validated()

    fun normalizeResult(result: AiDraftResult, learnedLabels: Set<String> = emptySet()): AiDraftResult =
        result.copy(entries = result.entries.map { normalizeDraft(it, learnedLabels) })

    private fun normalizeText(text: String): String = OfflineVocabulary.normalize(text)
        .replace('&', ' ')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAlias(normalizedText: String, alias: String): Boolean {
        val normalizedAlias = normalizeText(alias)
        if (normalizedAlias.isBlank()) return false
        return " $normalizedText ".contains(" $normalizedAlias ")
    }
}

