#import <CoreFoundation/CoreFoundation.h>
#import <Foundation/Foundation.h>
#import <Security/Security.h>
#include <string.h>

static inline CFMutableDictionaryRef convx_keychain_query(const char *service, const char *account) {
    CFStringRef serviceString = CFStringCreateWithCString(kCFAllocatorDefault, service, kCFStringEncodingUTF8);
    CFStringRef accountString = CFStringCreateWithCString(kCFAllocatorDefault, account, kCFStringEncodingUTF8);
    CFMutableDictionaryRef query = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks
    );
    CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword);
    CFDictionarySetValue(query, kSecAttrService, serviceString);
    CFDictionarySetValue(query, kSecAttrAccount, accountString);
    CFRelease(serviceString);
    CFRelease(accountString);
    return query;
}

static inline int convx_keychain_read(
    const char *service,
    const char *account,
    char *buffer,
    size_t capacity,
    size_t *length
) {
    CFMutableDictionaryRef query = convx_keychain_query(service, account);
    CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue);
    CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne);
    CFDataRef value = NULL;
    OSStatus status = SecItemCopyMatching(query, (CFTypeRef *)&value);
    CFRelease(query);
    if (status != errSecSuccess) return (int)status;

    CFIndex dataLength = CFDataGetLength(value);
    *length = (size_t)dataLength;
    if ((size_t)dataLength > capacity) {
        CFRelease(value);
        return (int)errSecBufferTooSmall;
    }
    memcpy(buffer, CFDataGetBytePtr(value), (size_t)dataLength);
    CFRelease(value);
    return (int)errSecSuccess;
}

static inline int convx_keychain_write(
    const char *service,
    const char *account,
    const char *bytes,
    size_t length
) {
    CFMutableDictionaryRef query = convx_keychain_query(service, account);
    CFDataRef value = CFDataCreate(kCFAllocatorDefault, (const UInt8 *)bytes, (CFIndex)length);
    CFMutableDictionaryRef updates = CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks
    );
    CFDictionarySetValue(updates, kSecValueData, value);

    OSStatus status = SecItemUpdate(query, updates);
    if (status == errSecItemNotFound) {
        CFDictionarySetValue(query, kSecValueData, value);
        CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly);
        status = SecItemAdd(query, NULL);
    }

    CFRelease(updates);
    CFRelease(value);
    CFRelease(query);
    return (int)status;
}

static inline int convx_keychain_clear(const char *service, const char *account) {
    CFMutableDictionaryRef query = convx_keychain_query(service, account);
    OSStatus status = SecItemDelete(query);
    CFRelease(query);
    return (int)status;
}
