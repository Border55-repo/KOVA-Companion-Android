import unittest
from unittest.mock import Mock, patch
import broadcast_announcement as broadcaster
import webpush

ID = 'a' * 64

class TargetingTests(unittest.TestCase):
    @patch.object(broadcaster, '_send')
    @patch.object(broadcaster, 'send_web_announcement')
    def test_test_never_calls_android(self, web, android):
        web.return_value = dict(targets=1, accepted=1, failed=0, expired=0)
        result = broadcaster.dispatch_announcement('Title', 'Body', 'id', audience='test', subscription_id=ID)
        android.assert_not_called()
        web.assert_called_once_with('Title', 'Body', 'id', audience='test', subscription_id=ID)
        self.assertEqual(result['android']['targets'], 0)

    @patch.object(broadcaster, 'send_web_announcement')
    def test_missing_test_id_cannot_fall_back_to_all(self, web):
        with self.assertRaises(ValueError):
            broadcaster.dispatch_announcement(audience='test')
        web.assert_not_called()

    @patch.object(broadcaster, 'send_web_announcement', return_value=dict(targets=0, accepted=0, failed=0, expired=0))
    def test_unknown_device_is_a_failure(self, web):
        with self.assertRaises(broadcaster.AnnouncementDeliveryError):
            broadcaster.dispatch_announcement(audience='test', subscription_id=ID)

    @patch.object(webpush, '_send_to_subscription', return_value=(True, True))
    @patch.object(webpush, '_list_subscription_documents')
    @patch.object(webpush, 'ensure_vapid_config', return_value={})
    @patch.object(webpush, '_credentials', return_value=(Mock(), 'project'))
    def test_filter_has_exactly_one_device_and_corps_filter_is_separate(self, credentials, config, docs, send):
        docs.return_value = [
            {'name':'subscriptions/'+ID,'fields':{'organizations':{'arrayValue':{'values':[{'stringValue':'A'}]}}}},
            {'name':'subscriptions/'+'b'*64,'fields':{'organizations':{'arrayValue':{'values':[{'stringValue':'B'}]}}}},
        ]
        result=webpush.send_web_announcement('Title','Body','id',audience='test',subscription_id=ID)
        self.assertEqual(result['targets'],1)
        self.assertEqual(send.call_args.args[0]['name'],'subscriptions/'+ID)
        send.reset_mock()
        result=webpush.send_web_announcement('Title','Body','id',audience='organization',organization='B')
        self.assertEqual(result['targets'],1)
        self.assertEqual(send.call_args.args[0]['name'],'subscriptions/'+'b'*64)

    @patch.object(broadcaster, '_send')
    @patch.object(broadcaster, '_credentials', return_value=(Mock(),'project'))
    @patch.object(broadcaster, 'REGISTRY_PATH')
    @patch.object(broadcaster, 'send_web_announcement', return_value=dict(targets=1,accepted=1,failed=0,expired=0))
    def test_corps_never_sends_all_users_topic(self, web, registry, credentials, android):
        registry.read_text.return_value='{"organizations":[{"code":"A"},{"code":"B"}]}'
        broadcaster.dispatch_announcement(audience='organization',organization='B')
        android.assert_called_once()
        self.assertEqual(android.call_args.args[2]['code'],'B')

if __name__ == '__main__':
    unittest.main()
