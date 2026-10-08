export function detectPlatform(ua='',touchPoints=0){
  if(/iphone|ipad|ipod/i.test(ua)||(/macintosh/i.test(ua)&&touchPoints>1))return 'ios';
  if(/android/i.test(ua))return 'android';
  return 'desktop';
}
