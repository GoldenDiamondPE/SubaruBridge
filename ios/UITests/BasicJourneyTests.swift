import XCTest

@MainActor final class BasicJourneyTests: XCTestCase {
    private func reveal(_ field: XCUIElement, app: XCUIApplication) {
        for _ in 0..<5 { if field.isHittable { return }; app.swipeUp() }
        XCTAssertTrue(field.isHittable)
    }
    private func dismissKeyboard(_ app: XCUIApplication) {
        if app.buttons["Done"].exists { app.buttons["Done"].tap() } else { app.swipeDown() }
    }
    private func openCalls(_ app: XCUIApplication) {
        let link=app.buttons["app-calls"]
        for _ in 0..<5 { if link.isHittable { break }; app.swipeDown() }
        link.tap()
    }
    func testAppCardsAndUnavailableActions() throws {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.staticTexts["iPhone companion preview"].waitForExistence(timeout: 20))
        for id in ["music", "navigation", "calls", "messages", "notifications"] {
            let link = app.buttons["app-" + id]
            if !link.isHittable { app.swipeUp() }
            XCTAssertTrue(link.waitForExistence(timeout: 5)); link.tap()
            if id == "music" {
                XCTAssertTrue(app.buttons["Choose library music"].waitForExistence(timeout: 5))
                for title in ["Previous", "Play", "Next"] { XCTAssertFalse(app.buttons[title].isEnabled) }
            }
            if id == "navigation" { XCTAssertFalse(app.buttons["Open directions on iPhone"].isEnabled) }
            if id == "calls" { XCTAssertFalse(app.buttons["Open iPhone calling prompt"].isEnabled) }
            if id == "messages" { XCTAssertFalse(app.buttons["Compose message"].isEnabled) }
            if id == "notifications" {
                XCTAssertTrue(app.staticTexts["SubaruBridge notifications"].waitForExistence(timeout: 5))
                XCTAssertFalse(app.buttons["local-notification-test"].isEnabled)
            }
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
        XCTAssertTrue(app.tabBars.buttons["Tools"].exists)
        app.tabBars.buttons["Tools"].tap()
        XCTAssertTrue(app.buttons["Inspect connected accessories"].waitForExistence(timeout: 5))
    }
    func testFavoriteValidationAndKeyboardDismissal() throws {
        continueAfterFailure=false
        let app=XCUIApplication();app.launch()
        let number=app.textFields["favorite-number"];reveal(number,app:app);number.tap();number.typeText("2");dismissKeyboard(app)
        openCalls(app);XCTAssertFalse(app.buttons["Open iPhone calling prompt"].isEnabled)
        app.navigationBars.buttons.element(boundBy:0).tap()
        reveal(number,app:app);number.tap();number.typeText(XCUIKeyboardKey.delete.rawValue+"2025550100");dismissKeyboard(app)
        openCalls(app);XCTAssertTrue(app.buttons["Open iPhone calling prompt"].isEnabled)
        app.navigationBars.buttons.element(boundBy:0).tap()
        reveal(number,app:app);number.tap();number.typeText(String(repeating:XCUIKeyboardKey.delete.rawValue,count:10));dismissKeyboard(app)
    }
    func testNotificationPermissionDenial() throws {
        continueAfterFailure=false
        let app=XCUIApplication();app.launch();app.buttons["app-notifications"].tap()
        let permission=app.buttons["notification-permission"];XCTAssertTrue(permission.waitForExistence(timeout:5));permission.tap()
        let alert=XCUIApplication(bundleIdentifier:"com.apple.springboard").alerts.firstMatch
        XCTAssertTrue(alert.waitForExistence(timeout:8))
        let deny=alert.buttons.matching(NSPredicate(format:"label MATCHES[c] %@","Don['’]t Allow")).firstMatch
        XCTAssertTrue(deny.exists);deny.tap()
        let status=app.staticTexts["notification-status"]
        XCTAssertTrue(status.waitForExistence(timeout:5))
        XCTAssertTrue(status.label.contains("Off — change this in iPhone Settings"))
        XCTAssertTrue(app.buttons["Open iPhone Settings"].exists)
        XCTAssertFalse(app.buttons["local-notification-test"].isEnabled)
    }
    func testToolsLocalJourneyAndExport() throws {
        continueAfterFailure=false
        let app=XCUIApplication();app.launch();app.tabBars.buttons["Tools"].tap()
        for stage in 1...4 {
            let tile=app.buttons["tool-\(stage)"]
            XCTAssertTrue(tile.waitForExistence(timeout:5));tile.tap()
            switch stage {
            case 1: XCTAssertTrue(app.staticTexts["A clear beginning."].exists)
            case 2:
                for _ in 0..<3 { app.buttons["Record local touch"].tap() }
                XCTAssertTrue(app.staticTexts["3"].exists)
            case 3: app.buttons["Read this iPhone"].tap()
            default:
                XCTAssertTrue(app.staticTexts["Local check progress"].exists)
                app.buttons["Save local result"].tap()
                XCTAssertTrue(app.staticTexts["Local checks complete"].waitForExistence(timeout:5))
                XCTAssertTrue(app.buttons["Share this local test result"].exists)
            }
            app.navigationBars.buttons.element(boundBy:0).tap()
        }
    }
}
