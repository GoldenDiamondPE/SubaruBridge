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
        XCTAssertTrue(app.staticTexts["unified-home"].waitForExistence(timeout: 20))
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
                XCTAssertTrue(app.staticTexts["notification-status"].waitForExistence(timeout: 5))
                XCTAssertFalse(app.buttons["local-notification-test"].isEnabled)
            }
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }
        XCTAssertTrue(app.tabBars.buttons["Tools"].exists)
        app.tabBars.buttons["Tools"].tap()
        reveal(app.buttons["Inspect connected accessories"], app: app)
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
        let app=XCUIApplication();app.launch();reveal(app.buttons["app-notifications"], app:app);app.buttons["app-notifications"].tap()
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
            XCTAssertTrue(tile.waitForExistence(timeout:5));reveal(tile, app:app);tile.tap()
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
    private func fill(_ key: String, _ value: String, app: XCUIApplication) {
        let field = app.textFields["garage-field-" + key]; reveal(field, app: app); field.tap()
        let old = (field.value as? String).flatMap { $0 == field.placeholderValue ? nil : $0 } ?? ""
        field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: old.count) + value); dismissKeyboard(app)
    }
    private func screenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIApplication().screenshot()); attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    func testGarageSavingHistoryAndRelaunch() throws {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launchArguments = ["--garage-ui-test", "--garage-reset", "-AppleLocale", "en_US"]; app.launch()
        XCTAssertTrue(app.staticTexts["unified-home"].waitForExistence(timeout:20)); screenshot("unified-home")
        app.tabBars.buttons["Garage"].tap(); XCTAssertTrue(app.buttons["garage-add-vehicle"].waitForExistence(timeout:10)); app.buttons["garage-add-vehicle"].tap()
        XCTAssertTrue(app.buttons["garage-save"].waitForExistence(timeout:5)); app.buttons["garage-save"].tap()
        XCTAssertTrue(app.buttons["garage-log-odometer"].waitForExistence(timeout:10)); app.buttons["garage-log-odometer"].tap()
        fill("distance", "81000", app:app); app.buttons["garage-save"].tap()
        XCTAssertTrue(app.buttons["garage-log-service"].waitForExistence(timeout:10)); screenshot("unified-garage")
        app.buttons["garage-log-service"].tap(); app.buttons["garage-choose-items"].tap()
        let oil = app.switches["garage-item-engine-oil"]; XCTAssertTrue(oil.waitForExistence(timeout:5)); oil.tap()
        app.switches["garage-item-oil-filter"].tap(); app.navigationBars.buttons.element(boundBy:0).tap()
        app.switches["garage-toggle-dateKnown"].tap(); fill("low", "70000", app:app)
        let range = app.switches["garage-toggle-range"]; reveal(range, app:app)
        range.coordinate(withNormalizedOffset: CGVector(dx: 0.92, dy: 0.5)).tap()
        XCTAssertTrue(app.textFields["garage-field-high"].waitForExistence(timeout:5)); fill("high", "74000", app:app)
        app.buttons["garage-save"].tap(); XCTAssertTrue(app.buttons["garage-log-service"].waitForExistence(timeout:10))
        app.segmentedControls["garage-sections"].buttons["Maintenance"].tap(); screenshot("unified-maintenance")
        app.segmentedControls["garage-sections"].buttons["History"].tap(); XCTAssertTrue(app.buttons.containing(.staticText, identifier:"Engine oil, Oil filter").firstMatch.waitForExistence(timeout:5))
        app.terminate(); app.launchArguments = ["--garage-ui-test", "-AppleLocale", "en_US"]; app.launch(); app.tabBars.buttons["Garage"].tap()
        XCTAssertTrue(app.buttons["garage-log-odometer"].waitForExistence(timeout:10)); XCTAssertTrue(app.staticTexts["81,000 mi"].exists)
        app.segmentedControls["garage-sections"].buttons["More"].tap(); app.buttons["garage-export"].tap()
        XCTAssertTrue(app.navigationBars["Export"].waitForExistence(timeout:8) || app.buttons["Save to Files"].exists || app.buttons["Export"].exists)
    }
    func testRecorderAndJavaScriptFreeDashboard() throws {
        continueAfterFailure = false
        let app = XCUIApplication(); app.launch(); XCTAssertTrue(app.staticTexts["unified-home"].waitForExistence(timeout:20))
        app.buttons["workspace-recorder"].tap(); app.buttons["recorder-start"].tap()
        XCTAssertTrue(app.buttons["recorder-stop"].isEnabled); app.buttons["Run phone self-check"].tap()
        let checks = app.staticTexts["phone-self-check-result"]; XCTAssertTrue(checks.label.contains("Maintenance engine: passed")); screenshot("unified-recorder")
        app.buttons["recorder-stop"].tap(); XCTAssertTrue(app.buttons["recorder-start"].isEnabled)
        app.tabBars.buttons["Dashboard"].tap()
        let check = app.webViews.links["Check"]; XCTAssertTrue(check.waitForExistence(timeout:15)); screenshot("unified-dashboard")
        check.tap(); let touch = app.webViews.links.matching(NSPredicate(format:"label CONTAINS %@", "2 Touch")).firstMatch
        XCTAssertTrue(touch.waitForExistence(timeout:5)); touch.tap()
        let send = app.webViews.buttons["Send touch to iPhone"]; XCTAssertTrue(send.waitForExistence(timeout:5)); send.tap()
        XCTAssertTrue(app.webViews.staticTexts.matching(NSPredicate(format:"label CONTAINS %@", "1")).firstMatch.exists)
        let appearance = app.webViews.links["Appearance"]; appearance.tap()
        let daylight = app.webViews.buttons["Daylight"]; XCTAssertTrue(daylight.waitForExistence(timeout:5)); daylight.tap()
        XCTAssertTrue(app.webViews.staticTexts["Saved on your iPhone."].waitForExistence(timeout:5))
    }
}
