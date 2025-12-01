import Foundation

enum ActivityControllerError: Error {
    case unexpected(String)

    var reason: String {
        switch self {
        case .unexpected(let message):
            return message
        }
    }
}
