import unittest
from unittest.mock import Mock, patch

import broadcast_announcement as broadcaster
import webpush
from sync import process_announcement_request


class AnnouncementTests(unittest.TestCase):
    @patch.object(broadcaster, "send_web_announcement", return_value=dict(targets=1, accepted=1, failed=0, expired=0))
    def test_global_reaches_pwa(self, send):
        result = broadcaster.dispatch_announcement("Title", "Body", "request-123")
        send.assert_called_once_with("Title", "Body", "request-123")
        self.assertEqual(result["pwa"]["accepted"], 1)
        self.assertNotIn("android", result)

    @patch.object(broadcaster, "send_web_announcement", return_value=dict(targets=1, accepted=0, failed=1, expired=0))
    def test_pwa_failure_is_reported(self, send):
        with self.assertRaises(broadcaster.AnnouncementDeliveryError) as error:
            broadcaster.dispatch_announcement()
        self.assertEqual(error.exception.delivery["pwa"]["failed"], 1)

    @patch.object(broadcaster, "send_web_announcement", return_value=dict(targets=1, accepted=1, failed=0, expired=0))
    def test_admin_command_records_pwa_result(self, send):
        db = Mock()
        ref = db.collection.return_value.document.return_value
        ref.get.return_value.to_dict.return_value = {"status": "requested", "requestId": "request-123"}
        process_announcement_request(db)
        result = ref.update.call_args_list[-1].args[0]
        self.assertEqual(result["status"], "completed")
        self.assertEqual(result["delivery"]["pwa"]["accepted"], 1)


class GlobalWebPushTests(unittest.TestCase):
    @staticmethod
    def subscription():
        return {"name": "subscriptions/one", "fields": {
            "endpoint": {"stringValue": "https://push.example.invalid/one"},
            "p256dh": {"stringValue": "public-key"},
            "auth": {"stringValue": "auth-key"},
        }}

    @patch.object(webpush.time, "sleep")
    @patch.object(webpush, "webpush")
    def test_transient_push_500_retries_once(self, send, sleep):
        class PushError(RuntimeError):
            response = Mock(status_code=500)
        with patch.object(webpush, "WebPushException", PushError):
            send.side_effect = [PushError("temporary"), None]
            result = webpush._send_to_subscription(
                self.subscription(), {"changeId": "one"}, {"privateKey": "key"}, Mock()
            )
        self.assertEqual(result, (True, True))
        self.assertEqual(send.call_count, 2)
        sleep.assert_called_once_with(1)

    @patch.object(webpush.time, "sleep")
    @patch.object(webpush, "webpush")
    def test_persistent_push_500_remains_failed(self, send, sleep):
        class PushError(RuntimeError):
            response = Mock(status_code=500)
        with patch.object(webpush, "WebPushException", PushError):
            send.side_effect = PushError("temporary")
            result = webpush._send_to_subscription(
                self.subscription(), {"changeId": "one"}, {"privateKey": "key"}, Mock()
            )
        self.assertEqual(result, (False, False))
        self.assertEqual(send.call_count, 2)
        sleep.assert_called_once_with(1)

    @patch.object(webpush, "_disable_subscription")
    @patch.object(webpush.time, "sleep")
    @patch.object(webpush, "webpush")
    def test_expired_push_does_not_retry(self, send, sleep, disable):
        class PushError(RuntimeError):
            response = Mock(status_code=410)
        with patch.object(webpush, "WebPushException", PushError):
            send.side_effect = PushError("expired")
            result = webpush._send_to_subscription(
                self.subscription(), {"changeId": "one"}, {"privateKey": "key"}, Mock()
            )
        self.assertEqual(result, (True, False))
        send.assert_called_once()
        sleep.assert_not_called()
        disable.assert_called_once()

    @patch.object(webpush, "_send_to_subscription", return_value=(True, True))
    @patch.object(webpush, "_list_subscription_documents")
    @patch.object(webpush, "ensure_vapid_config", return_value={})
    @patch.object(webpush, "_credentials", return_value=(Mock(), "project"))
    def test_legacy_clients_receive_global_announcements(self, credentials, config, documents, send):
        documents.return_value = [
            {"fields": {"enabled": {"booleanValue": True},
                        "notificationKinds": {"arrayValue": {"values": [{"stringValue": "added"}]}}}},
            {"fields": {"enabled": {"booleanValue": False}}},
        ]
        result = webpush.send_web_announcement("Title", "Body", "request-123")
        self.assertEqual(result["targets"], 1)
        self.assertEqual(result["accepted"], 1)
        send.assert_called_once()

    @patch.object(webpush, "_credentials", return_value=(None, None))
    def test_missing_web_credentials_fail(self, credentials):
        with self.assertRaises(RuntimeError):
            webpush.send_web_announcement("Title", "Body", "id")


class ActivityDeliveryTests(unittest.TestCase):
    @patch("push.send_web_notification", return_value=True)
    def test_accepted_web_push_marks_change_sent(self, web):
        from push import send_diff_notification
        event = {"id": "1", "description": "Vakt", "dateLabel": "24.09", "time": "18:00"}
        result = send_diff_notification({"code": "UllensakerRKH"},
                                        {"added": [event], "removed": [], "changed": []}, "https://www.kova.no/")
        web.assert_called_once()
        self.assertEqual(len(result), 1)

    @patch("push.send_web_notification", return_value=False)
    def test_failed_web_push_remains_pending(self, web):
        from push import send_diff_notification
        event = {"id": "1", "description": "Vakt", "dateLabel": "24.09", "time": "18:00"}
        result = send_diff_notification({"code": "UllensakerRKH"},
                                        {"added": [event], "removed": [], "changed": []}, "https://www.kova.no/")
        self.assertEqual(result, [])

    @patch("push.send_web_notification", return_value=True)
    def test_changes_above_batch_limit_remain_pending(self, web):
        from push import send_diff_notification
        events = [{"id": str(i), "description": f"Vakt {i}", "dateLabel": "24.09", "time": "18:00"} for i in range(12)]
        result = send_diff_notification({"code": "UllensakerRKH"},
                                        {"added": events, "removed": [], "changed": []}, "https://www.kova.no/")
        self.assertEqual(len(result), 10)
        self.assertEqual(web.call_count, 10)


if __name__ == "__main__":
    unittest.main()

