import os
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

import runtime_credentials


class RuntimeCredentialsTests(unittest.TestCase):
    @patch.dict(os.environ, {}, clear=True)
    @patch('runtime_credentials.google.auth.default')
    def test_local_checks_do_not_silently_use_other_credentials(self, default):
        self.assertEqual(runtime_credentials.credentials_for(['scope']), (None, None))
        default.assert_not_called()

    @patch.dict(os.environ, {'K_SERVICE': 'dispatch-admin'}, clear=True)
    @patch('runtime_credentials.google.auth.default')
    def test_managed_runtime_uses_assigned_identity(self, default):
        credentials = Mock()
        default.return_value = (credentials, 'kova-companion')
        self.assertEqual(runtime_credentials.credentials_for(['scope']), (credentials, 'kova-companion'))
        default.assert_called_once_with(scopes=['scope'])
        credentials.refresh.assert_called_once()

    @patch.dict(os.environ, {'FIREBASE_SERVICE_ACCOUNT_JSON': '{"project_id":"kova-companion"}'}, clear=True)
    @patch('runtime_credentials.service_account.Credentials.from_service_account_info')
    def test_github_still_uses_existing_secret(self, certificate):
        credentials, project = runtime_credentials.credentials_for(['scope'])
        self.assertEqual(project, 'kova-companion')
        self.assertIs(credentials, certificate.return_value)
        credentials.refresh.assert_called_once()


class DirectDispatchTests(unittest.TestCase):
    @staticmethod
    def event(status='requested', exists=True):
        after = Mock()
        after.exists = exists
        after.to_dict.return_value = {'action': 'announcement', 'source': 'changelog', 'status': status}
        return SimpleNamespace(data=SimpleNamespace(after=after))

    def test_queued_admin_write_invokes_the_same_bridge_worker(self):
        import main
        with patch.object(main, 'get_app'), patch.object(main.firestore, 'client') as client, patch.object(main, 'process_announcement_request') as worker:
            main.process_admin_event(self.event())
            worker.assert_called_once_with(client.return_value)

    def test_status_writes_do_not_send_again(self):
        import main
        with patch.object(main, 'process_announcement_request') as worker:
            for status in ('running', 'completed', 'failed'):
                main.process_admin_event(self.event(status))
            main.process_admin_event(self.event(exists=False))
            main.process_admin_event(SimpleNamespace(data=None))
            worker.assert_not_called()

    def test_unrelated_command_is_ignored(self):
        import main
        event = self.event()
        event.data.after.to_dict.return_value['action'] = 'bridgeSync'
        with patch.object(main, 'process_announcement_request') as worker:
            main.process_admin_event(event)
            worker.assert_not_called()


if __name__ == '__main__':
    unittest.main()
