import 'package:flutter_test/flutter_test.dart';
import 'package:jabook/core/constants/category_constants.dart';
import 'package:jabook/core/parse/category_parser.dart';

void main() {
  group('CategoryParser.parseCategories', () {
    test('extracts forums and subforums, skips ignored', () async {
      const html = '''
<!doctype html><html><body>
  <div id="c-${CategoryConstants.audiobooksCategoryId}">
    <table>
      <tr id="f-100"><td><h4 class="forumlink"><a href="viewforum.php?f=100">Аудиокниги</a></h4>
        <div class="subforums">
          <a href="viewforum.php?f=101">Радиоспектакли</a>
          <a href="viewforum.php?f=102">История</a>
        </div>
      </td></tr>
      <tr id="f-200"><td><h4 class="forumlink"><a href="viewforum.php?f=200">Обсуждение</a></h4></td></tr>
    </table>
  </div>
</body></html>
''';

      final parser = CategoryParser();
      final cats = await parser.parseCategories(html);
      expect(cats.length, 1);
      expect(cats.first.id, '100');
      expect(cats.first.subcategories.length, 2);
    });
  });

  group('CategoryParser.parseCategoryTopics', () {
    test('parses topics and skips ads rows', () async {
      const html = '''
<!doctype html><html><body>
  <table>
    <tr class="hl-tr banner"><td>ad</td></tr>
    <tr class="hl-tr" data-topic_id="123">
      <td><a class="torTopic tt-text" href="viewtopic.php?t=123">Тема 123</a></td>
      <td><span class="seedmed"><b>7</b></span></td>
      <td><span class="leechmed"><b>1</b></span></td>
      <td><a class="f-dl dl-stub">10 MB</a></td>
      <td><p class="med" title="Торрент скачан"><b>50</b></p></td>
    </tr>
  </table>
</body></html>
''';

      final parser = CategoryParser();
      final topics = await parser.parseCategoryTopics(html);
      expect(topics.length, 1);
      expect(topics.first['id'], '123');
      expect(topics.first['seeders'], 7);
      expect(topics.first['leechers'], 1);
    });
  });
}


