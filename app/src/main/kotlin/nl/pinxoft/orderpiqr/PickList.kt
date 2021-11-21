package nl.pinxoft.orderpiqr

/**
 * List of items to pick, constructed from a (scanned) string.
 * Each line contains one item to pick, in a free format.
 */
class PickList(public val PickListString: String) {
    var PickItems: List<List<String>>
        private set

    var LastPickedIndex: Int = -1

    init {
        val lines = PickListString.split("\r\n", "\\r", "\\n").filter { it.isNotEmpty() }
        val pickItems = mutableListOf<List<String>>()
        for (line in lines) {
            val lineItems = line.split(" ", "\t").filter { it.isNotEmpty() }
            pickItems.add(lineItems)
        }
        PickItems = pickItems
    }

    fun ItemToScan(): List<String> {
        val indexToScan = LastPickedIndex + 1
        if (indexToScan > PickItems.lastIndex)
            return listOf("Einde van pickbon.", "Je bent klaar.", "Pak de volgende pickbon.")
        else
            return PickItems[indexToScan]
    }

    /**
     * Return True if the scan code was found in the item to scan.
     */
    fun Pick(scanResult: String): Boolean {
        return ItemToScan().contains(scanResult)
    }
}