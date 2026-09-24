import unittest
from unittest.mock import Mock, patch

import broadcast_announcement as broadcaster
import webpush
from sync import process_announcement_request


class AnnouncementTests(unittest.TestCase):
    def setUp(self):
        self.patches = [
            patch.object(broadcaster, '_credentials', return_value=(Mock(), 'project')),
            patch.object(broadcaster, 'REGISTRY_PATH'),
            patch.object(broadcaster, '_send'),
            patch.object(broadcaster, 'send_web_announcement', return_value={
                'targets': 1, 'accepted': 1, 'failed': 0, 'expired': 0}),
        ]
        self.credentials, self.registry, self.android, self.pwa = [p.start() for p in self.patches]
        self.registry.read_text.return_value = '{"organizations":[{"code":"UllensakerRKH"},{"code":"EHRKH"}]}'
        for p in self.patches:
            self.addCleanup(p.stop)

    def test_dispatch_calls_both_platforms_with_same_id(self):
        result = broadcaster.dispatch_announcement('Title', 'Body', 'request-123')
        self.assertEqual(self.android.call_count, 3)
        self.pwa.assert_called_once_with('Title', 'Body', 'request-123')
        self.assertEqual(result['android']['accepted'], 3)
        self.assertEqual(result['pwa']['accepted'], 1)

    def test_android_failure_does_not_block_pwa(self):
        self.android.side_effect = RuntimeError('FCM failed')
        with self.assertRaises(broadcaster.AnnouncementDeliveryError) as error:
            broadcaster.dispatch_announcement('Title', 'Body', 'request-123')
        self.pwa.assert_called_once()
        self.assertEqual(error.exception.delivery['android']['failed'], 3)

    def test_pwa_failure_is_not_reported_as_success(self):
        self.pwa.return_value = {'targets': 1, 'accepted': 0, 'failed': 1, 'expired': 0}
        with self.assertRaises(broadcaster.AnnouncementDeliveryError):
            broadcaster.dispatch_announcement()

    def test_missing_credentials_still_attempts_web_transport(self):
        self.credentials.return_value = (None, None)
        with self.assertRaises(broadcaster.AnnouncementDeliveryError):
            broadcaster.dispatch_announcement()
        self.pwa.assert_called_once()

    def test_admin_command_records_both_transport_results(self):
        db = Mock()
        ref = db.collection.return_value.document.return_value
        ref.get.return_value.to_dict.return_value = {'status': 'requested', 'requestId': 'request-123'}
        process_announcement_request(db)
        result = ref.update.call_args_list[-1].args[0]
        self.assertEqual(result['status'], 'completed')
        self.assertEqual(result['delivery']['pwa']['accepted'], 1)

    def test_admin_command_records_partial_failure(self):
        self.pwa.return_value = {'targets': 1, 'accepted': 0, 'failed': 1, 'expired': 0}
        db = Mock()
        ref = db.collection.return_value.document.return_value
        ref.get.return_value.to_dict.return_value = {'status': 'requested', 'requestId': 'request-123'}
        process_announcement_request(db)
        result = ref.update.call_args_list[-1].args[0]
        self.assertEqual(result['status'], 'failed')
        self.assertEqual(result['delivery']['android']['accepted'], 3)


class GlobalWebPushTests(unittest.TestCase):
    @patch.object(webpush, '_send_to_subscription', return_value=(True, True))
    @patch.object(webpush, '_list_subscription_documents')
    @patch.object(webpush, 'ensure_vapid_config', return_value={})
    @patch.object(webpush, '_credentials', return_value=(Mock(), 'project'))
    def test_legacy_clients_receive_global_announcements(self, credentials, config, documents, send):
        documents.return_value = [
            {'fields': {'enabled': {'booleanValue': True},
                        'notificationKinds': {'arrayValue': {'values': [{'stringValue': 'added'}]}}}},
            {'fields': {'enabled': {'booleanValue': False}}},
        ]
        result = webpush.send_web_announcement('Title', 'Body', 'request-123')
        self.assertEqual(result['targets'], 1)
        self.assertEqual(result['accepted'], 1)
        send.assert_called_once()

    @patch.object(webpush, '_credentials', return_value=(None, None))
    def test_missing_web_credentials_fail(self, credentials):
        with self.assertRaises(RuntimeError):
            webpush.send_web_announcement('Title', 'Body', 'id')

class ActivityDeliveryTests(unittest.TestCase):
    @patch('push._credentials', return_value=(Mock(), 'project'))
    @patch('push._send', side_effect=RuntimeError('FCM failed'))
    @patch('push.send_web_notification', return_value=True)
    def test_activity_web_push_is_independent_of_fcm(self, web, android, credentials):
        from push import send_diff_notification
        event = {'id': '1', 'description': 'Vakt', 'dateLabel': '24.09', 'time': '18:00'}
        result = send_diff_notification({'code': 'UllensakerRKH'}, {'added': [event], 'removed': [], 'changed': []}, 'https://www.kova.no/')
        web.assert_called_once()
        self.assertEqual(result, [])

    @patch('push._credentials', return_value=(Mock(), 'project'))
    @patch('push._send')
    @patch('push.send_web_notification', return_value=True)
    def test_changes_above_batch_limit_remain_pending(self, web, android, credentials):
        from push import send_diff_notification
        events = [{'id': str(i), 'description': f'Vakt {i}', 'dateLabel': '24.09', 'time': '18:00'} for i in range(12)]
        result = send_diff_notification({'code': 'UllensakerRKH'}, {'added': events, 'removed': [], 'changed': []}, 'https://www.kova.no/')
        self.assertEqual(len(result), 10)
        self.assertEqual(web.call_count, 10)
        self.assertTrue(all(call.args[3] == 'added' for call in web.call_args_list))


if __name__ == '__main__':
    unittest.main()
