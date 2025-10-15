using System;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace FTBQuestEditor.WinUI.Converters;

public sealed class StringToVisibilityConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        var hasContent = value is string text && !string.IsNullOrWhiteSpace(text);
        return hasContent ? Visibility.Visible : Visibility.Collapsed;
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
    {
        throw new NotSupportedException();
    }
}
