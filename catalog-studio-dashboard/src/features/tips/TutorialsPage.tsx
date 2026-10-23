import { VideoLibraryPage } from "./VideoLibraryPage";

export function TutorialsPage() {
  return (
    <VideoLibraryPage
      apiPath="/tutorials"
      titleKey="tutorials.title"
      subtitleKey="tutorials.subtitle"
      queryKey="ecommerce-tutorials"
    />
  );
}
