package nl.pinxoft.orderpiqr

enum class ScanState {
    Unknown,
    NewPickList,
    WaitingForItem,
    Success,
    Failure
}